# The EBS CSI driver is what turns a PersistentVolumeClaim into a real EBS disk.
# Kubernetes' built-in EBS support was removed, so without this driver the Postgres
# StatefulSet's PVC stays Pending forever. Same IRSA pattern as the ALB controller:
# the AWS permissions go to the driver's own service account only.

module "ebs_csi_irsa" {
    source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
    version = "~> 5.39"

    role_name             = "idp-ebs-csi"
    attach_ebs_csi_policy = true

    oidc_providers = {
        main = {
            provider_arn               = module.eks.oidc_provider_arn
            namespace_service_accounts = ["kube-system:ebs-csi-controller-sa"]
        }
    }
}
