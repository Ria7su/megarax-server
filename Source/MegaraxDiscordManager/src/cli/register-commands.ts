import { registerCommands } from "../commands.js";
import { withGuild } from "./shared.js";

await withGuild(async ({ client, env }) => {
  await registerCommands(client, env);
  console.log("Comando /megarax registrado en el servidor de prueba.");
});
