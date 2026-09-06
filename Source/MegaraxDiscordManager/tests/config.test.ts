import { describe, expect, it } from "vitest";
import { loadManifest } from "../src/config.js";

describe("server manifest", () => {
  it("validates the production YAML", async () => {
    const manifest = await loadManifest("config/server.yml");
    expect(manifest.network.name).toBe("Megarax Network");
    expect(manifest.categories.find((category) => category.key === "staff")?.staffOnly).toBe(true);
    expect(manifest.roles.find((role) => role.key === "founder")?.autoAssign).toBe(false);
  });

  it("never exposes restricted roles as selectable", async () => {
    const manifest = await loadManifest("config/server.yml");
    const restricted = new Set([
      "founder",
      "administrator",
      "developer",
      "game_master",
      "moderator",
      "support",
      "helper",
      "premium",
      "beta_tester",
      "clan_leader",
      "content_creator",
    ]);
    expect(manifest.roles.filter((role) => restricted.has(role.key) && role.selectable)).toEqual(
      [],
    );
  });
});
