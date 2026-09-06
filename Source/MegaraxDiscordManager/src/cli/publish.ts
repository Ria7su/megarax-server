import { publishManagedContent } from "../discord/apply.js";
import { rolePanel } from "../discord/roles.js";
import { ticketPanel } from "../discord/tickets.js";
import { readState } from "../storage.js";
import { withGuild } from "./shared.js";

await withGuild(async ({ guild, manifest }) => {
  const published = await publishManagedContent(guild, manifest);
  const state = await readState(guild.id);
  const ticketsId = state.resources["channel:ticket_create"]?.id;
  const rolesId = state.resources["channel:role_select"]?.id;
  const ticketChannel = ticketsId ? guild.channels.cache.get(ticketsId) : undefined;
  const roleChannel = rolesId ? guild.channels.cache.get(rolesId) : undefined;

  if (ticketChannel?.isTextBased() && !ticketChannel.isThread()) {
    await ticketChannel.send(ticketPanel());
  }
  if (roleChannel?.isTextBased() && !roleChannel.isThread()) {
    await roleChannel.send(rolePanel(manifest));
  }
  console.log(`Publicados ${published} contenidos y los paneles interactivos.`);
});
