-- Dev Services init script — mirrors the CNPG cluster bootstrap postInitSQL
-- (deployment/postgres/postgres-cluster.yaml → spec.bootstrap.initdb.postInitSQL)
CREATE EXTENSION IF NOT EXISTS vector;
