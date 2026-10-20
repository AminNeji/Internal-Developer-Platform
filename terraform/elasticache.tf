resource "aws_elasticache_subnet_group" "idp" {
    name = "idp-redis-subnet"
    subnet_ids = module.vpc.private_subnets
}

resource "aws_elasticache_cluster" "idp_redis" {
    cluster_id = "idp-redis"
    engine = "redis"
    node_type = "cache.t3.micro"
    num_cache_nodes = 1

    subnet_group_name = aws_elasticache_subnet_group.idp.name
    security_group_ids = [aws_security_group.redis_sg.id]
}