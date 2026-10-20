resource "aws_db_subnet_group" "idp" {
    name="idp-db-subnet"
    subnet_ids=module.vpc.private_subnets
}

resource "aws_db_instance" "idp_db"{
    identifier = "idp-database"
    engine="postgres"
    engine_version="15"
    instance_class = "db.t3.micro"

    allocated_storage = 20
    db_name="idp"
    username="postgres"
    password=var.db_password

    vpc_security_group_ids=[aws_security_group.rds_sg.id]
    db_subnet_group_name=aws_db_subnet_group.idp.name

    skip_final_snapshot=true
}