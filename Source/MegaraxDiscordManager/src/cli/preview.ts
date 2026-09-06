import { previewGuild, summarizePlan } from "../service.js";
import { withGuild } from "./shared.js";

await withGuild(async ({ guild }) => {
  const { plan, planFile } = await previewGuild(guild);
  console.log(summarizePlan(plan));
  console.log(`\nPlan guardado: ${planFile}`);
  if (!plan.validated) process.exitCode = 2;
});
