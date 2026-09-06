import {
  PermissionFlagsBits,
  PermissionsBitField,
  type Guild,
  type PermissionResolvable,
} from "discord.js";
import type { Manifest, RoleSpec } from "../config.js";

const flags: Record<RoleSpec["permissions"][number], bigint> = {
  Administrator: PermissionFlagsBits.Administrator,
  ManageGuild: PermissionFlagsBits.ManageGuild,
  ManageChannels: PermissionFlagsBits.ManageChannels,
  ManageRoles: PermissionFlagsBits.ManageRoles,
  ManageMessages: PermissionFlagsBits.ManageMessages,
  ManageThreads: PermissionFlagsBits.ManageThreads,
  KickMembers: PermissionFlagsBits.KickMembers,
  BanMembers: PermissionFlagsBits.BanMembers,
  ModerateMembers: PermissionFlagsBits.ModerateMembers,
  ViewAuditLog: PermissionFlagsBits.ViewAuditLog,
  ViewChannel: PermissionFlagsBits.ViewChannel,
  SendMessages: PermissionFlagsBits.SendMessages,
  EmbedLinks: PermissionFlagsBits.EmbedLinks,
  AttachFiles: PermissionFlagsBits.AttachFiles,
  ReadMessageHistory: PermissionFlagsBits.ReadMessageHistory,
};

export function rolePermissions(role: RoleSpec): PermissionsBitField {
  return new PermissionsBitField(role.permissions.map((permission) => flags[permission]));
}

export const requiredBotPermissions = new PermissionsBitField([
  PermissionFlagsBits.ViewChannel,
  PermissionFlagsBits.SendMessages,
  PermissionFlagsBits.EmbedLinks,
  PermissionFlagsBits.AttachFiles,
  PermissionFlagsBits.ReadMessageHistory,
  PermissionFlagsBits.ManageChannels,
  PermissionFlagsBits.ManageRoles,
  PermissionFlagsBits.ManageMessages,
  PermissionFlagsBits.ManageThreads,
  PermissionFlagsBits.UseApplicationCommands,
]);

export function permissionReport(guild: Guild, manifest: Manifest): string[] {
  const me = guild.members.me;
  if (!me) return ["No se pudo resolver el miembro del bot."];
  const missing = me.permissions.missing(
    requiredBotPermissions as unknown as PermissionResolvable[],
  );
  const botRolePosition = me.roles.highest.position;
  const protectedNames = new Set(
    guild.roles.cache
      .filter((role) => role.managed || role.permissions.has(PermissionFlagsBits.Administrator))
      .map((role) => role.name),
  );
  const report = [
    `Permisos faltantes: ${missing.length === 0 ? "ninguno" : missing.join(", ")}`,
    `Rol más alto del bot: ${me.roles.highest.name} (posición ${botRolePosition})`,
    `Roles administrativos/protegidos existentes: ${[...protectedNames].join(", ") || "ninguno"}`,
  ];
  const undelegable = new Set<string>();
  for (const role of manifest.roles) {
    for (const permission of role.permissions) {
      if (!me.permissions.has(flags[permission])) undelegable.add(permission);
    }
  }
  report.push(
    `Permisos que el bot no puede delegar: ${
      undelegable.size === 0 ? "ninguno" : [...undelegable].join(", ")
    }`,
  );
  if (manifest.roles.some((role) => role.manualPermissions.includes("Administrator"))) {
    report.push(
      "Founder requiere Administrator como permiso manual; el bot no lo concede ni lo asigna.",
    );
  }
  return report;
}
