package idpbackend.idp_backend.service;


import org.eclipse.jgit.api.Git;
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

    @Value("${git.repo.url}")
    private String GIT_REPO_URL;
    @Value("${git.username}")
    private String GIT_USERNAME;
    @Value("${git.password}")
    private String GIT_PASSWORD ;

    public void commitAndPushManifests(String namespace, Map<String, String> manifests) throws Exception {

        // 1. Définir un dossier local temporaire pour cloner le repo
        File localRepoDir = new File("./tmp-gitops-repo");

        // Nettoyage si le dossier existe déjà d'un ancien build (optionnel mais recommandé)
        if (localRepoDir.exists()) {
            deleteDirectory(localRepoDir);
        }

        // Configuration de l'authentification Git
        UsernamePasswordCredentialsProvider credentials =
                new UsernamePasswordCredentialsProvider(GIT_USERNAME, GIT_PASSWORD);

        System.out.println("Clonage du dépôt GitOps...");
        // 2. Clone du dépôt
        try (Git git = Git.cloneRepository()
                .setURI(GIT_REPO_URL)
                .setBranch("dev")
                .setDirectory(localRepoDir)
                .setCredentialsProvider(credentials)
                .call()) {

            // 3. Création du dossier pour le nouvel environnement dans le repo cloné
            // Exemple : ./tmp-gitops-repo/environments/dev/
            String targetPathStr = localRepoDir.getAbsolutePath() + "/environments/" + namespace + "/";
            Files.createDirectories(Paths.get(targetPathStr));

            // 4. Écriture de chaque manifeste (Reçu depuis ton TemplateService)
            for (Map.Entry<String, String> entry : manifests.entrySet()) {
                Path filePath = Paths.get(targetPathStr + entry.getKey());
                Files.writeString(filePath, entry.getValue(), StandardCharsets.UTF_8);
                System.out.println("Fichier préparé pour Git : " + entry.getKey());
            }

            System.out.println("Exécution de Git Add, Commit et Push...");

            // 5. Git Add : Ajoute tous les nouveaux fichiers au staging
            git.add()
                    .addFilepattern(".")
                    .call();

            // 6. Git Commit
            git.commit()
                    .setMessage("feat(idp): déploiement automatique de l'environnement : " + namespace)
                    .setAuthor("IDP Platform", "idp@monentreprise.com")
                    .call();

            // 7. Git Push
            git.push()
                    .setCredentialsProvider(credentials)
                    .call();

            System.out.println("Manifestes poussés sur Git avec succès !");
        } finally {
            // Nettoyage final du dossier temporaire après le push
            deleteDirectory(localRepoDir);
        }
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
