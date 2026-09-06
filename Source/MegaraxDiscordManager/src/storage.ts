import { createHash, randomUUID } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import type { ChangePlan, ManagedState, Snapshot } from "./types.js";

export const emptyState = (guildId: string): ManagedState => ({
  schemaVersion: 1,
  guildId,
  updatedAt: new Date().toISOString(),
  resources: {},
});

export async function readState(guildId: string): Promise<ManagedState> {
  try {
    return JSON.parse(await readFile(resolve("state/managed-state.json"), "utf8")) as ManagedState;
  } catch {
    return emptyState(guildId);
  }
}

export async function writeState(state: ManagedState): Promise<void> {
  await mkdir("state", { recursive: true });
  await writeFile(resolve("state/managed-state.json"), JSON.stringify(state, null, 2), "utf8");
}

export async function writeSnapshot(snapshot: Snapshot): Promise<string> {
  await mkdir("backups", { recursive: true });
  const file = resolve(`backups/guild-${safeStamp()}-${randomUUID().slice(0, 8)}.json`);
  await writeFile(file, JSON.stringify(snapshot, null, 2), "utf8");
  return file;
}

export async function writePlan(plan: ChangePlan): Promise<string> {
  await mkdir("plans", { recursive: true });
  const file = resolve(`plans/plan-${plan.planId}.json`);
  await writeFile(file, JSON.stringify(plan, null, 2), "utf8");
  return file;
}

export async function readPlan(path: string): Promise<ChangePlan> {
  return JSON.parse(await readFile(resolve(path), "utf8")) as ChangePlan;
}

export function fingerprint(value: unknown): string {
  return createHash("sha256").update(JSON.stringify(value)).digest("hex");
}

function safeStamp(): string {
  return new Date()
    .toISOString()
    .replaceAll(":", "-")
    .replace(/\.\d{3}Z$/, "Z");
}
