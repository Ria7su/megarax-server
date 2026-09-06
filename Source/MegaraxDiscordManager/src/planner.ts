import { randomUUID } from "node:crypto";
import type { Manifest } from "./config.js";
import { fingerprint } from "./storage.js";
import type { ChangePlan, ManagedState, PlanAction, Snapshot } from "./types.js";

export function createPlan(
  manifest: Manifest,
  snapshot: Snapshot,
  state: ManagedState,
  snapshotFile: string,
): ChangePlan {
  const actions: PlanAction[] = [];
  const rolesById = new Map(snapshot.roles.map((role) => [role.id, role]));
  const rolesByName = groupBy(snapshot.roles, (role) => role.name);
  const channelsById = new Map(snapshot.channels.map((channel) => [channel.id, channel]));
  const channelsByName = groupBy(snapshot.channels, (channel) => channel.name);

  for (const role of manifest.roles) {
    const key = `role:${role.key}`;
    const managed = state.resources[key];
    const desired = {
      name: role.name,
      color: role.color,
      permissions: role.permissions,
      manualPermissions: role.manualPermissions,
    };
    actions.push(decideResource("role", key, role.name, desired, managed, rolesById, rolesByName));
  }

  for (const category of manifest.categories) {
    const categoryKey = `category:${category.key}`;
    const desiredCategory = {
      name: category.name,
      staffOnly: category.staffOnly,
      everyoneVisible: category.everyoneVisible,
    };
    actions.push(
      decideResource(
        "category",
        categoryKey,
        category.name,
        desiredCategory,
        state.resources[categoryKey],
        channelsById,
        channelsByName,
      ),
    );
    for (const channel of category.channels) {
      const channelKey = `channel:${channel.key}`;
      const desiredChannel = {
        name: channel.name,
        type: channel.type,
        parentKey: categoryKey,
        readOnly: channel.readOnly,
        tags: channel.tags ?? [],
        content: channel.content ?? null,
        permissionPolicyVersion: 2,
      };
      actions.push(
        decideResource(
          "channel",
          channelKey,
          channel.name,
          desiredChannel,
          state.resources[channelKey],
          channelsById,
          channelsByName,
        ),
      );
    }
  }

  return {
    schemaVersion: 1,
    planId: randomUUID(),
    guildId: snapshot.guild.id,
    manifestHash: fingerprint(manifest),
    snapshotFile,
    createdAt: new Date().toISOString(),
    validated: !actions.some((action) => action.action === "conflict"),
    actions,
  };
}

function decideResource<T extends { id: string; name: string }>(
  kind: "role" | "category" | "channel",
  key: string,
  name: string,
  desired: Record<string, unknown>,
  managed: ManagedState["resources"][string] | undefined,
  byId: Map<string, T>,
  byName: Map<string, T[]>,
): PlanAction {
  const desiredFingerprint = fingerprint(desired);
  if (managed) {
    const existing = byId.get(managed.id);
    if (!existing) {
      return {
        action: "conflict",
        kind,
        key,
        name,
        reason: "El ID administrado ya no existe; requiere repair.",
        desired,
      };
    }
    if (managed.fingerprint === desiredFingerprint && existing.name === name) {
      return {
        action: "noop",
        kind,
        key,
        name,
        targetId: existing.id,
        reason: "Sin cambios.",
        desired,
      };
    }
    return {
      action: "update",
      kind,
      key,
      name,
      targetId: existing.id,
      reason: "El manifiesto cambió o existe deriva manual.",
      desired,
    };
  }
  const matches = byName.get(name) ?? [];
  if (matches.length > 0) {
    const conflict: PlanAction = {
      action: "conflict",
      kind,
      key,
      name,
      reason:
        "Existe un recurso no administrado con el mismo nombre; no se adoptará automáticamente.",
      desired,
    };
    if (matches[0]) conflict.targetId = matches[0].id;
    return conflict;
  }
  return { action: "create", kind, key, name, reason: "Recurso ausente en el servidor.", desired };
}

function groupBy<T, K>(values: T[], key: (value: T) => K): Map<K, T[]> {
  const result = new Map<K, T[]>();
  for (const value of values) result.set(key(value), [...(result.get(key(value)) ?? []), value]);
  return result;
}
