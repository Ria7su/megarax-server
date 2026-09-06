export type ManagedKind = "role" | "category" | "channel" | "message";

export interface ManagedState {
  schemaVersion: 1;
  guildId: string;
  updatedAt: string;
  resources: Record<string, { id: string; kind: ManagedKind; fingerprint: string }>;
}

export interface Snapshot {
  createdAt: string;
  guild: { id: string; name: string; ownerId: string };
  roles: {
    id: string;
    name: string;
    position: number;
    permissions: string;
    managed: boolean;
  }[];
  channels: {
    id: string;
    name: string;
    type: number;
    position: number;
    parentId: string | null;
    permissionOverwrites: { id: string; allow: string; deny: string; type: number }[];
  }[];
}

export type PlanActionType = "create" | "update" | "conflict" | "noop";

export interface PlanAction {
  action: PlanActionType;
  kind: ManagedKind;
  key: string;
  name: string;
  reason: string;
  targetId?: string;
  desired?: Record<string, unknown>;
}

export interface ChangePlan {
  schemaVersion: 1;
  planId: string;
  guildId: string;
  manifestHash: string;
  snapshotFile: string;
  createdAt: string;
  validated: boolean;
  actions: PlanAction[];
}
