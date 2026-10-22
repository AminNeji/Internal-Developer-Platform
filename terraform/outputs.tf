output "cluster_endpoint" {
    value = module.eks.cluster_endpoint
    description = "EKS cluster endpoint"
}

output "redis_endpoint" {
    value = aws_elasticache_cluster.idp_redis.cache_nodes[0].address
    description = "Redis cluster endpoint"
}