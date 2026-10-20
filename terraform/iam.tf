data "aws_iam_policy_document"  "ec2_assume_role" {
	statement {
		effect = "Allow"

		actions = ["sts:AssumeRole"]

		principals {
			type        = "Service"
			identifiers = ["ec2.amazonaws.com"]
		}
	}
}

resource "aws_iam_role" "idp" {
	name               = "idp-role"
	assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json
}

resource "aws_iam_role_policy_attachment" "idp_read_only" {
	role       = aws_iam_role.idp.name
	policy_arn = "arn:aws:iam::aws:policy/ReadOnlyAccess"
}
