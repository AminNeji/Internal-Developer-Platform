variable "cluster_name" {
    type = string
    default= "idp-cluster"
}
variable "ecr_repositories" {
  description = "List of ECR repository names to create"
  type        = list(string)
  default     = [
    "idp-frontend",
    "idp-backend"
  ]
}