module "eks" {
    source="terraform-aws-modules/eks/aws"
    version="19.0.0"

    cluster_name=var.cluster_name
    cluster_version= "1.34"

    vpc_id= module.vpc.vpc_id
    subnet_ids=module.vpc.private_subnets

    cluster_endpoint_public_access = true

    # creates the OIDC provider that lets a Kubernetes service account assume an IAM role
    enable_irsa = true

    eks_managed_node_groups = {
        idp_node_group = {
            desired_size = 2
            max_size     = 3
            min_size     = 1
            instance_types = ["t3.small"]
            # Amazon Linux 2 node images are not published for recent EKS versions
            ami_type       = "AL2023_x86_64_STANDARD"
        }
    }
}