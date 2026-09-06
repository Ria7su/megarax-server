import {
  ActionRowBuilder,
  ButtonBuilder,
  ButtonStyle,
  EmbedBuilder,
  type ButtonInteraction,
  type GuildMember,
} from "discord.js";
import type { Manifest } from "../config.js";
import { readState } from "../storage.js";

export function rolePanel(manifest: Manifest) {
  const selectable = manifest.roles.filter((role) => role.selectable);
  const rows: ActionRowBuilder<ButtonBuilder>[] = [];
  for (let index = 0; index < selectable.length; index += 5) {
    rows.push(
      new ActionRowBuilder<ButtonBuilder>().addComponents(
        ...selectable
          .slice(index, index + 5)
          .map((role) =>
            new ButtonBuilder()
              .setCustomId(`role:toggle:${role.key}`)
              .setLabel(role.name)
              .setStyle(ButtonStyle.Secondary),
          ),
      ),
    );
  }
  return {
    embeds: [
      new EmbedBuilder()
        .setColor(0xd5b478)
        .setTitle("Elige tus roles")
        .setDescription(
          "Selecciona idioma, juego e intereses. Pulsa nuevamente para retirar un rol.",
        ),
    ],
    components: rows,
  };
}

export async function handleRoleButton(
  interaction: ButtonInteraction,
  manifest: Manifest,
): Promise<boolean> {
  if (!interaction.customId.startsWith("role:toggle:")) return false;
  if (!interaction.guild || !interaction.member) return true;
  const key = interaction.customId.split(":")[2];
  const spec = manifest.roles.find((role) => role.key === key && role.selectable);
  if (
    !spec ||
    spec.group === "staff" ||
    ["premium", "beta_tester", "clan_leader", "content_creator"].includes(spec.key)
  ) {
    await interaction.reply({
      content: "Ese rol no puede asignarse desde este panel.",
      ephemeral: true,
    });
    return true;
  }
  const state = await readState(interaction.guild.id);
  const roleId = state.resources[`role:${spec.key}`]?.id;
  const role = roleId ? interaction.guild.roles.cache.get(roleId) : undefined;
  if (!role?.editable) {
    await interaction.reply({
      content: "El rol no está disponible o está por encima del bot.",
      ephemeral: true,
    });
    return true;
  }
  const member = interaction.member as GuildMember;
  if (member.roles.cache.has(role.id)) {
    await member.roles.remove(role, "Megarax Discord Manager: selección de rol");
    await interaction.reply({ content: `Rol retirado: ${role.name}`, ephemeral: true });
  } else {
    await member.roles.add(role, "Megarax Discord Manager: selección de rol");
    await interaction.reply({ content: `Rol asignado: ${role.name}`, ephemeral: true });
  }
  return true;
}
