import { permissionReport } from "../discord/permissions.js";
import { withGuild } from "./shared.js";

await withGuild(({ guild, manifest }) => {
  console.log(permissionReport(guild, manifest).join("\n"));
  return Promise.resolve();
});
