resource "aws_security_group" "rds_sg"{
    name="idp-rds-sg"
    description="Security group for RDS instance"
    vpc_id=module.vpc.vpc_id

    ingress {
        from_port=5432
        to_port=5432
        protocol="tcp"
        security_groups=[module.eks.node_security_group_id]
    }
}


resource "aws_security_group" "redis_sg" {
  name   = "idp-redis-sg"
  vpc_id = module.vpc.vpc_id

  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [module.eks.node_security_group_id]
  }
}