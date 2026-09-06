package handlers.admincommandhandlers;

import org.l2jmobius.gameserver.handler.IAdminCommandHandler;
import org.l2jmobius.gameserver.model.actor.Player;

import custom.MegaraxHuntContracts.MegaraxHuntContracts;
import custom.MegaraxWantedMonsters.MegaraxWantedMonsters;

public class AdminWantedMonsters implements IAdminCommandHandler
{
	private static final String[] COMMANDS={"admin_wanted_status","admin_wanted_spawn","admin_wanted_escape","admin_wanted_kill","admin_wanted_contributions","admin_wanted_seals","admin_wanted_claim","admin_wanted_reload","admin_wanted_enable","admin_wanted_disable","admin_wanted_despawn"};
	@Override public String[] getCommandList(){return COMMANDS;}
	@Override public boolean onCommand(String command,Player admin)
	{
		try{String[]a=command.trim().split("\\s+");MegaraxWantedMonsters s=MegaraxWantedMonsters.getInstance();switch(a[0]){case"admin_wanted_status":admin.sendMessage(s.getStatusText());break;case"admin_wanted_spawn":admin.sendMessage(s.forceSpawn(a.length>1?a[1]:"",a.length>2?a[2]:"")?"Wanted spawned.":"Wanted spawn rejected.");break;case"admin_wanted_escape":case"admin_wanted_despawn":s.escape();admin.sendMessage("Active Wanted escaped/despawned.");break;case"admin_wanted_kill":s.simulateDeath(admin);admin.sendMessage("Wanted defeat simulated.");break;case"admin_wanted_contributions":for(String x:s.getContributionText())admin.sendMessage(x);break;case"admin_wanted_seals":if(a.length<3)throw new IllegalArgumentException("account amount");admin.sendMessage(MegaraxHuntContracts.changeWantedSeals(admin,a[1],Long.parseLong(a[2]),"ADMIN_WANTED","ADMIN")?"Wanted Seals updated.":"Update rejected.");break;case"admin_wanted_claim":s.claimPending(admin);break;case"admin_wanted_reload":admin.sendMessage(MegaraxWantedMonsters.reloadConfig()?"Wanted XML reloaded.":"Reload failed.");break;case"admin_wanted_enable":MegaraxWantedMonsters.setEnabled(true);admin.sendMessage("Wanted enabled.");break;case"admin_wanted_disable":MegaraxWantedMonsters.setEnabled(false);admin.sendMessage("Wanted disabled.");break;default:admin.sendMessage("//wanted_spawn [template] [zone] | status | escape | kill | contributions | seals account amount | claim | reload | enable | disable");}}
		catch(Exception e){admin.sendMessage("Wanted admin error: "+e.getMessage());}return true;
	}
}
