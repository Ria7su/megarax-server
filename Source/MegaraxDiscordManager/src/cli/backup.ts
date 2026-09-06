import { captureSnapshot } from "../discord/snapshot.js";
import { writeSnapshot } from "../storage.js";
import { withGuild } from "./shared.js";

await withGuild(async ({ guild }) =>
  console.log(await writeSnapshot(await captureSnapshot(guild))),
);
