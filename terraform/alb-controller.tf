# The AWS Load Balancer Controller turns the `ingressClassName: alb` Ingresses that
# TemplateService renders into real Application Load Balancers. Without it those
# Ingress objects are created and never get an address.

module "alb_controller_irsa" {
    source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
    version = "~> 5.39"

    role_name                              = "idp-alb-controller"
    attach_load_balancer_controller_policy = true

    oidc_providers = {
        main = {
            provider_arn               = module.eks.oidc_provider_arn
            namespace_service_accounts = ["kube-system:aws-load-balancer-controller"]
        }
    }
}

resource "helm_release" "alb_controller" {
    name       = "aws-load-balancer-controller"
    repository = "https://aws.github.io/eks-charts"
    chart      = "aws-load-balancer-controller"
    version    = "1.8.1"
    namespace  = "kube-system"

    set = [
        {
            name  = "clusterName"
            value = module.eks.cluster_name
        },
        {
            name  = "region"
            value = "eu-north-1"
        },
        {
            name  = "vpcId"
            value = module.vpc.vpc_id
        },
        {
            name  = "serviceAccount.create"
            value = "true"
        },
        {
            name  = "serviceAccount.name"
            value = "aws-load-balancer-controller"
        },
        # the dots in the annotation key are escaped so Helm does not read them as nesting
        {
            name  = "serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn"
            value = module.alb_controller_irsa.iam_role_arn
        },
    ]

    depends_on = [
        module.eks,
        module.alb_controller_irsa,
    ]
}
