resource "aws_ecr_repository" "idp_registry" {
    image_tag_mutability = "MUTABLE"

    for_each             = toset(var.ecr_repositories)
    name                 = each.value

    encryption_configuration {
        encryption_type = "AES256"
    }
    force_delete = true
}
