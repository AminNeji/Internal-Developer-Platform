resource "aws_ecr_repository" "idp_registry" {
    image_tag_mutability = "MUTABLE"

    for_each             = toset(var.ecr_repositories)
    name                 = each.value

    encryption_configuration {
        encryption_type = "AES256"
    }
    force_delete = true
}

# environments live 72 hours at most, so their built images aren't needed after that
resource "aws_ecr_lifecycle_policy" "user_apps" {
    repository = aws_ecr_repository.idp_registry["idp-user-apps"].name

    policy = jsonencode({
        rules = [{
            rulePriority = 1
            description  = "Delete images 4 days after they are pushed"
            selection = {
                tagStatus   = "any"
                countType   = "sinceImagePushed"
                countUnit   = "days"
                countNumber = 4
            }
            action = { type = "expire" }
        }]
    })
}
