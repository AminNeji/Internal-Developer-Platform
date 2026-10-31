# ExternalDNS watches Ingresses and keeps Route 53 in step with them: the portal's own record
# and one record per environment are created and removed automatically.
# The hosted zone is not managed here: it costs almost nothing and must survive a destroy,
# because the domain's name servers point at it.

data "aws_route53_zone" "platform" {
    name = var.domain
}

module "external_dns_irsa" {
    source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
    version = "~> 5.39"

    role_name                     = "idp-external-dns"
    attach_external_dns_policy    = true
    external_dns_hosted_zone_arns = [data.aws_route53_zone.platform.arn]

    oidc_providers = {
        main = {
            provider_arn               = module.eks.oidc_provider_arn
            namespace_service_accounts = ["kube-system:external-dns"]
        }
    }
}

resource "helm_release" "external_dns" {
    name       = "external-dns"
    repository = "https://kubernetes-sigs.github.io/external-dns/"
    chart      = "external-dns"
    version    = "1.23.0"
    namespace  = "kube-system"

    values = [yamlencode({
        provider      = { name = "aws" }
        sources       = ["ingress"]
        domainFilters = [var.domain]
        # sync also deletes records when an Ingress goes away; the default would leave them behind
        policy     = "sync"
        registry   = "txt"
        txtOwnerId = var.cluster_name
        # react to Ingress changes straight away instead of waiting for the next one-minute pass
        triggerLoopOnEvent = true
        env = [{ name = "AWS_DEFAULT_REGION", value = "eu-north-1" }]
        serviceAccount = {
            name        = "external-dns"
            annotations = { "eks.amazonaws.com/role-arn" = module.external_dns_irsa.iam_role_arn }
        }
    })]

    depends_on = [
        module.eks,
        module.external_dns_irsa,
        # same reason as the ALB controller: its webhook must not be mid-install
        helm_release.alb_controller,
    ]
}
