import { describe, expect, it } from "vitest";
import { createPlan } from "../src/planner.js";
import type { Manifest } from "../src/config.js";
import type { ManagedState, Snapshot } from "../src/types.js";

const manifest = {
  schemaVersion: 1,
  network: { name: "Megarax Network", locale: "es", englishEnabled: true, bannerUrl: null },
  features: { worldOfWarcraft: false, webhooks: false, tickets: true, selectableRoles: true },
  placeholders: {},
  status: {},
  roles: [
    {
      key: "player",
      name: "🎮 Player",
      group: "community",
      permissions: [],
      autoAssign: false,
      protected: false,
      selectable: false,
    },
  ],
  categories: [
    {
      key: "start",
      name: "START",
      everyoneVisible: true,
      staffOnly: false,
      channels: [{ key: "welcome", name: "welcome", type: "text", readOnly: true }],
    },
  ],
} satisfies Manifest;

const snapshot: Snapshot = {
  createdAt: new Date(0).toISOString(),
  guild: { id: "1", name: "Test", ownerId: "2" },
  roles: [],
  channels: [],
};

const state: ManagedState = {
  schemaVersion: 1,
  guildId: "1",
  updatedAt: new Date(0).toISOString(),
  resources: {},
};

describe("createPlan", () => {
  it("creates missing managed resources without deletions", () => {
    const plan = createPlan(manifest, snapshot, state, "backup.json");
    expect(plan.validated).toBe(true);
    expect(plan.actions.filter((action) => action.action === "create")).toHaveLength(3);
    expect(plan.actions.some((action) => action.action === ("delete" as never))).toBe(false);
  });

  it("reports an unmanaged name collision as a conflict", () => {
    const plan = createPlan(
      manifest,
      {
        ...snapshot,
        roles: [{ id: "9", name: "🎮 Player", position: 1, permissions: "0", managed: false }],
      },
      state,
      "backup.json",
    );
    expect(plan.validated).toBe(false);
    expect(plan.actions[0]?.action).toBe("conflict");
  });
});
