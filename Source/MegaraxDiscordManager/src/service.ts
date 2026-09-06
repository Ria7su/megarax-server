import type { Guild } from "discord.js";
import { loadManifest } from "./config.js";
import { captureSnapshot } from "./discord/snapshot.js";
import { createPlan } from "./planner.js";
import { fingerprint, readState, writePlan, writeSnapshot } from "./storage.js";
import type { ChangePlan } from "./types.js";

export async function previewGuild(guild: Guild): Promise<{ plan: ChangePlan; planFile: string }> {
  const manifest = await loadManifest();
  const state = await readState(guild.id);
  const snapshot = await captureSnapshot(guild);
  const snapshotFile = await writeSnapshot(snapshot);
  const plan = createPlan(manifest, snapshot, state, snapshotFile);
  plan.manifestHash = fingerprint(manifest);
  const planFile = await writePlan(plan);
  return { plan, planFile };
}

export function summarizePlan(plan: ChangePlan): string {
  const counts = plan.actions.reduce<Record<string, number>>((result, action) => {
    result[action.action] = (result[action.action] ?? 0) + 1;
    return result;
  }, {});
  const lines = [
    `Plan ${plan.planId}`,
    `Validado: ${plan.validated ? "sí" : "no"}`,
    `Crear: ${counts.create ?? 0}`,
    `Actualizar: ${counts.update ?? 0}`,
    `Conflictos: ${counts.conflict ?? 0}`,
    `Sin cambios: ${counts.noop ?? 0}`,
    "",
  ];
  for (const action of plan.actions.filter((item) => item.action !== "noop")) {
    lines.push(`[${action.action.toUpperCase()}] ${action.kind} ${action.name}: ${action.reason}`);
  }
  return lines.join("\n");
}
