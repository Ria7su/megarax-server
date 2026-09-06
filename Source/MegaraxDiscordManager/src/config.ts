import "dotenv/config";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import YAML from "yaml";
import { z } from "zod";

const envSchema = z.object({
  DISCORD_BOT_TOKEN: z.string().min(20),
  DISCORD_CLIENT_ID: z.string().regex(/^\d{15,22}$/),
  DISCORD_GUILD_ID: z.string().regex(/^\d{15,22}$/),
  DISCORD_OWNER_ID: z.string().regex(/^\d{15,22}$/),
  NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
});

const permissionSchema = z.enum([
  "Administrator",
  "ManageGuild",
  "ManageChannels",
  "ManageRoles",
  "ManageMessages",
  "ManageThreads",
  "KickMembers",
  "BanMembers",
  "ModerateMembers",
  "ViewAuditLog",
  "ViewChannel",
  "SendMessages",
  "EmbedLinks",
  "AttachFiles",
  "ReadMessageHistory",
]);

const roleSchema = z.object({
  key: z.string().regex(/^[a-z0-9_]+$/),
  name: z.string().min(1).max(100),
  color: z
    .string()
    .regex(/^#[0-9A-Fa-f]{6}$/)
    .optional(),
  group: z.enum(["staff", "community", "interest"]),
  permissions: z.array(permissionSchema).default([]),
  manualPermissions: z.array(permissionSchema).default([]),
  autoAssign: z.boolean().default(false),
  protected: z.boolean().default(false),
  selectable: z.boolean().default(false),
});

const channelSchema = z.object({
  key: z.string().regex(/^[a-z0-9_]+$/),
  name: z.string().min(1).max(100),
  type: z.enum(["text", "announcement", "forum", "media", "voice"]),
  readOnly: z.boolean().default(false),
  content: z.string().optional(),
  tags: z.array(z.string().max(20)).max(20).optional(),
});

const categorySchema = z.object({
  key: z.string().regex(/^[a-z0-9_]+$/),
  name: z.string().min(1).max(100),
  everyoneVisible: z.boolean().default(false),
  staffOnly: z.boolean().default(false),
  channels: z.array(channelSchema),
});

export const manifestSchema = z.object({
  schemaVersion: z.literal(1),
  network: z.object({
    name: z.literal("Megarax Network"),
    locale: z.string(),
    englishEnabled: z.boolean(),
    bannerUrl: z.url().nullable(),
  }),
  features: z.object({
    worldOfWarcraft: z.boolean(),
    webhooks: z.boolean(),
    tickets: z.boolean(),
    selectableRoles: z.boolean(),
  }),
  placeholders: z.record(z.string(), z.string()),
  status: z.record(z.string(), z.string()),
  roles: z.array(roleSchema),
  categories: z.array(categorySchema),
});

export type Env = z.infer<typeof envSchema>;
export type Manifest = z.infer<typeof manifestSchema>;
export type RoleSpec = Manifest["roles"][number];
export type CategorySpec = Manifest["categories"][number];
export type ChannelSpec = CategorySpec["channels"][number];

export function loadEnv(): Env {
  return envSchema.parse(process.env);
}

export async function loadManifest(path = "config/server.yml"): Promise<Manifest> {
  const raw = await readFile(resolve(path), "utf8");
  const manifest = manifestSchema.parse(YAML.parse(raw));
  assertUnique(
    manifest.roles.map((role) => role.key),
    "role key",
  );
  assertUnique(
    manifest.roles.map((role) => role.name),
    "role name",
  );
  assertUnique(
    manifest.categories.map((category) => category.key),
    "category key",
  );
  assertUnique(
    manifest.categories.flatMap((category) => category.channels.map((channel) => channel.key)),
    "channel key",
  );
  return manifest;
}

function assertUnique(values: string[], label: string): void {
  const duplicates = values.filter((value, index) => values.indexOf(value) !== index);
  if (duplicates.length > 0)
    throw new Error(`Duplicate ${label}: ${[...new Set(duplicates)].join(", ")}`);
}
