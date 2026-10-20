terraform{
    required_version = ">= 1.5"

    required_providers {
        aws = {
            source  = "hashicorp/aws"
            version = "~> 5.0"
        }
        helm = {
            source  = "hashicorp/helm"
            version = "~> 3.0"
        }
    }
}
provider "aws" {
    region="eu-north-1"
}

# The cluster does not exist when Terraform configures this provider on a first
# apply, so `exec` fetches a token at apply time instead of at plan time.
provider "helm" {
    kubernetes = {
        host                   = module.eks.cluster_endpoint
        cluster_ca_certificate = base64decode(module.eks.cluster_certificate_authority_data)

        exec = {
            api_version = "client.authentication.k8s.io/v1beta1"
            command     = "aws"
            args        = ["eks", "get-token", "--cluster-name", module.eks.cluster_name]
        }
    }
}