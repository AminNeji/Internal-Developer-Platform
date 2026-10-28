export interface ServiceConfig {
  serviceName: string;
  port: number;
  path: string;
  image: string;
}

export interface CreateEnvironmentReq {
  team: string;
  type: string;
  services: ServiceConfig[];
}

export interface Environment {
  id: number;
  envId: string;
  team: string;
  type: string;
  status: string;
  url: string | null;
  // null for environments created before login existed
  createdBy: string | null;
  createdAt: string;
  lastActiveAt: string | null;
  services: ServiceConfig[];
}

export type SyncStatus = 'Synced' | 'OutOfSync' | 'Unknown';

export type HealthStatus =
  | 'Healthy'
  | 'Progressing'
  | 'Degraded'
  | 'Suspended'
  | 'Missing'
  | 'Unknown';

export interface ApplicationStatus {
  syncStatus: SyncStatus;
  healthStatus: HealthStatus;
  url: string | null;
}