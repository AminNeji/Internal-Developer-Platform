# ArgoCD is the only in-cluster component Terraform installs. Everything else
# (Prometheus, Grafana, and the generated environments) is declared as ArgoCD
# Applications in the GitOps repository, so Git stays the single source of truth.

resource "helm_release" "argocd" {
    name             = "argocd"
    repository       = "https://argoproj.github.io/argo-helm"
    chart            = "argo-cd"
    version          = "7.9.1"
    namespace        = "argocd"
    create_namespace = true

    # the backend logs in as this account. Unlike an admin session, which expires after 24h,
    # an API key generated for it never expires. Its rights stop at Applications.
    values = [yamlencode({
        configs = {
            cm   = { "accounts.idp-backend" = "apiKey" }
            rbac = { "policy.csv" = "p, idp-backend, applications, *, */*, allow" }
        }
    })]

    set = [
        # serve plain HTTP: the backend talks to argocd-server over the cluster network,
        # and the chart's default self-signed certificate would be rejected by the JVM
        {
            name  = "configs.params.server\\.insecure"
            value = "true"
        },
    ]

    depends_on = [module.eks]
}
