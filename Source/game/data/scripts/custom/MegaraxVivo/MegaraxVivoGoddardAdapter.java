package custom.MegaraxVivo;

import org.l2jmobius.gameserver.model.actor.Player;

/**
 * Narrow gameplay bridge for the first VIVO vertical slice.
 *
 * Existing systems can call one of these methods after they have completed
 * their own validation and rewards. VIVO never removes items, changes the
 * original reward, or grants currency here. The caller must provide a stable
 * event key; the ledger's unique constraint makes retries idempotent.
 */
public final class MegaraxVivoGoddardAdapter
{
	private MegaraxVivoGoddardAdapter()
	{
	}

	public static MegaraxVivoService.ContributionResult notifySupplyDelivery(Player player, String uniqueEventKey, String sourceKey, int baseAmount)
	{
		return contribute(player, uniqueEventKey, "SUPPLY_DELIVERY", sourceKey, "SUPPLY", baseAmount);
	}

	public static MegaraxVivoService.ContributionResult notifyCraftCompleted(Player player, String uniqueEventKey, String sourceKey, int baseAmount)
	{
		return contribute(player, uniqueEventKey, "CRAFT_COMPLETED", sourceKey, "CRAFT", baseAmount);
	}

	public static MegaraxVivoService.ContributionResult notifySpoilRecovered(Player player, String uniqueEventKey, String sourceKey, int baseAmount)
	{
		return contribute(player, uniqueEventKey, "SPOIL_RECOVERED", sourceKey, "SPOIL", baseAmount);
	}

	public static MegaraxVivoService.ContributionResult notifyServiceCompleted(Player player, String uniqueEventKey, String sourceKey, int baseAmount)
	{
		return contribute(player, uniqueEventKey, "SERVICE_COMPLETED", sourceKey, "SERVICE", baseAmount);
	}

	public static MegaraxVivoService.ContributionResult notifyEscortMilestone(Player player, String uniqueEventKey, String sourceKey, int baseAmount)
	{
		return contribute(player, uniqueEventKey, "ESCORT_MILESTONE", sourceKey, "ESCORT", baseAmount);
	}

	private static MegaraxVivoService.ContributionResult contribute(Player player, String uniqueEventKey, String sourceType, String sourceKey, String category, int baseAmount)
	{
		// Force configuration/service initialization even if this bridge is called
		// before the Community Board handler has been constructed.
		MegaraxVivoService.getInstance();
		if (!MegaraxVivoConfig.GODDARD_ADAPTER_ENABLED)
		{
			return MegaraxVivoService.ContributionResult.rejected("ADAPTER_DISABLED", "El adaptador VIVO de Goddard esta en validacion privada.");
		}
		return MegaraxVivoService.contribute(player, MegaraxVivoConfig.PILOT_NEED_KEY, uniqueEventKey, sourceType, sourceKey, category, baseAmount);
	}
}
