package me.funky.praxi.profile.meta;

import me.funky.praxi.Locale;
import me.funky.praxi.arena.Arena;
import me.funky.praxi.kit.Kit;
import me.funky.praxi.match.participant.MatchGamePlayer;
import me.funky.praxi.participant.GameParticipant;
import me.funky.praxi.profile.Profile;
import me.funky.praxi.profile.ProfileState;
import me.funky.praxi.profile.hotbar.Hotbar;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import me.funky.praxi.Praxi;
import me.funky.praxi.match.Match;
import me.funky.praxi.match.impl.BasicTeamMatch;
import me.funky.praxi.util.CC;
import me.funky.praxi.util.ChatComponentBuilder;
import me.funky.praxi.util.ChatHelper;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@Getter
public class ProfileRematchData {

	private final UUID key;
	private final UUID sender;
	private final UUID target;
	private Kit kit;
	private Arena arena;
	private boolean sent;
	private boolean receive;
	private boolean cancelled;
	private long timestamp;

	public ProfileRematchData(UUID key, UUID sender, UUID target, Kit kit) {
		this.key = key;
		this.sender = sender;
		this.target = target;
		this.kit = kit;
		this.timestamp = System.currentTimeMillis();
	}

	public void request() {
		this.validate();

		if (cancelled) {
			return;
		}

		Player sender = Bukkit.getPlayer(this.sender);
		Player target = Bukkit.getPlayer(this.target);

		if (sender == null || target == null) {
			return;
		}

		Profile senderProfile = Profile.getByUuid(sender.getUniqueId());
		Profile targetProfile = Profile.getByUuid(target.getUniqueId());

		if (senderProfile.isBusy()) {
			sender.sendMessage(CC.RED + "You cannot duel right now.");
			return;
		}

		if (targetProfile.isBusy()) {
			sender.sendMessage(CC.RED + "You cannot duel right now.");
			return;
		}

		for (String line : Locale.REMATCH_SENT_REQUEST.formatLines(target.getName(), arena.getName())) {
			sender.sendMessage(line);
		}

		List<BaseComponent[]> components = new ArrayList<>();

		for (String line : Locale.REMATCH_RECEIVED_REQUEST.formatLines(sender.getName(), arena.getName())) {
			BaseComponent[] lineComponents = new ChatComponentBuilder("")
					.parse(line)
					.attachToEachPart(ChatHelper.hover(Locale.REMATCH_RECEIVED_REQUEST_HOVER.format()))
					.attachToEachPart(ChatHelper.click("/rematch"))
					.create();

			components.add(lineComponents);
		}

		for (BaseComponent[] line : components) {
			target.spigot().sendMessage(line);
		}

		this.sent = true;

		targetProfile.getRematchData().receive = true;
	}

	public void accept() {
		this.validate();

		Player sender = Praxi.get().getServer().getPlayer(this.sender);
		Player target = Praxi.get().getServer().getPlayer(this.target);

		if (sender == null || target == null || !sender.isOnline() || !target.isOnline()) {
			return;
		}

		Profile senderProfile = Profile.getByUuid(sender.getUniqueId());
		Profile targetProfile = Profile.getByUuid(target.getUniqueId());

		if (senderProfile.isBusy()) {
			sender.sendMessage(CC.RED + "You cannot duel right now.");
			return;
		}

		if (targetProfile.isBusy()) {
			sender.sendMessage(target.getDisplayName() + CC.RED + " is currently busy.");
			return;
		}

		Arena arena = this.arena;

		if (arena == null || arena.isActive()) {
			arena = Arena.getRandomArena(kit);
		}

		if (arena == null) {
			sender.sendMessage(CC.RED + "Tried to start a match but there are no available arenas.");
			return;
		}

		arena.setActive(true);

		MatchGamePlayer playerA = new MatchGamePlayer(sender.getUniqueId(), sender.getName());
		MatchGamePlayer playerB = new MatchGamePlayer(target.getUniqueId(), target.getName());

		GameParticipant<MatchGamePlayer> participantA = new GameParticipant<>(playerA);
		GameParticipant<MatchGamePlayer> participantB = new GameParticipant<>(playerB);

		Match match = new BasicTeamMatch(null, kit, arena, false, participantA, participantB);
		match.start();
	}

	public void validate() {
		for (UUID uuid : new UUID[]{ sender, target }) {
			Player player = Bukkit.getPlayer(uuid);

			if (player != null) {
				Profile profile = Profile.getByUuid(player.getUniqueId());

				if (profile != null) {
					if (profile.getRematchData() == null) {
						this.cancel();
						return;
					}

					if (!profile.getRematchData().getKey().equals(this.key)) {
						this.cancel();
						return;
					}

					if (!(profile.getState() == ProfileState.LOBBY || profile.getState() == ProfileState.QUEUEING)) {
						this.cancel();
						return;
					}

					if (System.currentTimeMillis() >= timestamp + 30_000L) {
						this.cancel();
						return;
					}
				}
			}
		}
	}

	public void cancel() {
		this.cancelled = true;

		for (UUID uuid : new UUID[]{ sender, target }) {
			Player player = Bukkit.getPlayer(uuid);

			if (player != null) {
				Profile profile = Profile.getByUuid(player.getUniqueId());
				profile.setRematchData(null);

				if (profile.getState() == ProfileState.LOBBY) {
					Hotbar.giveHotbarItems(player);
				}
			}
		}
	}

}
