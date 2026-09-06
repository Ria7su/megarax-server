import { applyPlan } from "../discord/apply.js";
import { readPlan } from "../storage.js";
import { withGuild } from "./shared.js";

const planArg = process.argv.find((value) => value.startsWith("--plan="))?.slice(7);
const confirmation = process.argv.includes("--confirm");
if (!planArg || !confirmation) {
  throw new Error("Uso seguro: pnpm apply -- --plan=plans/plan-<uuid>.json --confirm");
}

await withGuild(async ({ guild, manifest }) => {
  const plan = await readPlan(planArg);
  await applyPlan(guild, manifest, plan);
  console.log(`Plan ${plan.planId} aplicado correctamente.`);
});
