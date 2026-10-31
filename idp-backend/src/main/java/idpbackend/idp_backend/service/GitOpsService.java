package idpbackend.idp_backend.service;


import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Service
public class GitOpsService {

    private static final int MAX_PUSH_ATTEMPTS = 3;

    @Value("${git.repo.url}")
    private String GIT_REPO_URL;
    @Value("${git.username}")
    private String GIT_USERNAME;
    @Value("${git.password}")
    private String GIT_PASSWORD ;

    public void commitAndPushManifests(String namespace, Map<String, String> manifests) throws Exception {
        commitAndPush("feat(idp): déploiement automatique de l'environnement : " + namespace, (git, localRepoDir) -> {
            // Création du dossier pour le nouvel environnement dans le repo cloné
            String targetPathStr = localRepoDir.getAbsolutePath() + "/environments/" + namespace + "/";
            Files.createDirectories(Paths.get(targetPathStr));

            // Écriture de chaque manifeste (Reçu depuis ton TemplateService)
            for (Map.Entry<String, String> entry : manifests.entrySet()) {
                Path filePath = Paths.get(targetPathStr + entry.getKey());
                Files.writeString(filePath, entry.getValue(), StandardCharsets.UTF_8);
                System.out.println("Fichier préparé pour Git : " + entry.getKey());
            }

            // Git Add : Ajoute tous les nouveaux fichiers au staging
            git.add()
                    .addFilepattern(".")
                    .call();
        });
    }

    // does nothing if the folder is already gone, so a retried destroy is safe
    public void deleteEnvironment(String namespace) throws Exception {
        commitAndPush("feat(idp): suppression de l'environnement : " + namespace, (git, localRepoDir) ->
                git.rm()
                        .addFilepattern("environments/" + namespace)
                        .call());
    }

    private void commitAndPush(String message, RepoChange change) throws Exception {
        // the backend, its other replica and CI all push to this branch, so a push can lose the
        // race; starting again from a fresh clone puts this change on top of the winner's commit
        for (int attempt = 1; attempt <= MAX_PUSH_ATTEMPTS; attempt++) {
            if (tryCommitAndPush(message, change)) {
                return;
            }
            System.out.println("Push rejeté, tentative " + attempt + "/" + MAX_PUSH_ATTEMPTS);
        }
        throw new IllegalStateException("Push Git rejeté " + MAX_PUSH_ATTEMPTS + " fois : " + message);
    }

    // false means the remote moved on and the push was rejected
    private boolean tryCommitAndPush(String message, RepoChange change) throws Exception {
        // Dossier temporaire propre à cet appel : le job de nettoyage et une requête HTTP
        // peuvent tourner en même temps et ne doivent pas partager le même clone
        File localRepoDir = Files.createTempDirectory("idp-gitops-").toFile();

        // Configuration de l'authentification Git
        UsernamePasswordCredentialsProvider credentials =
                new UsernamePasswordCredentialsProvider(GIT_USERNAME, GIT_PASSWORD);

        System.out.println("Clonage du dépôt GitOps...");
        try (Git git = Git.cloneRepository()
                .setURI(GIT_REPO_URL)
                .setBranch("dev")
                .setDirectory(localRepoDir)
                .setCredentialsProvider(credentials)
                .call()) {

            change.apply(git, localRepoDir);

            if (git.status().call().isClean()) {
                return true;
            }

            System.out.println("Exécution de Git Commit et Push...");

            git.commit()
                    .setMessage(message)
                    .setAuthor("IDP Platform", "idp@monentreprise.com")
                    .call();

            // JGit ne lève pas d'exception quand le push est rejeté :
            // il faut lire le statut de chaque référence
            for (PushResult result : git.push().setCredentialsProvider(credentials).call()) {
                for (RemoteRefUpdate update : result.getRemoteUpdates()) {
                    if (update.getStatus() == RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD
                            || update.getStatus() == RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED) {
                        return false;
                    }
                    if (update.getStatus() != RemoteRefUpdate.Status.OK) {
                        throw new IllegalStateException("Push Git échoué : " + update.getStatus()
                                + " " + update.getMessage());
                    }
                }
            }

            System.out.println("Changements poussés sur Git avec succès !");
            return true;
        } finally {
            // Nettoyage final du dossier temporaire après le push
            deleteDirectory(localRepoDir);
        }
    }

    @FunctionalInterface
    private interface RepoChange {
        void apply(Git git, File localRepoDir) throws Exception;
    }

    // Méthode utilitaire pour supprimer un dossier et son contenu de manière récursive
    private void deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        directoryToBeDeleted.delete();
    }


}
