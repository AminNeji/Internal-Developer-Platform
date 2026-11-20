export interface ServiceConfig {
  serviceName: string;
  port: number;
  path: string;
  image: string;
  healthPath: string;
  env: Record<string, string>;
  // set instead of a ready-made image when the platform builds the image from Git
  gitUrl?: string | null;
  gitRef?: string | null;
  dockerfilePath?: string | null;
}

export interface CreateEnvironmentReq {
  team: string;
  type: string;
  ttlHours: number;
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
  // null for environments created before TTLs existed; those never expire
  expiresAt: string | null;
  services: ServiceConfig[];
  // GitHub Actions page of the image build; null when no image was built
  buildUrl: string | null;
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