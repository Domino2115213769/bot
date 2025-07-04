package pl.dominus;

import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.Map;
import java.util.Properties;

public class Bot extends ListenerAdapter {

    private final String guildId;
    private final String verifyRoleId;
    private final String welcomeChannelId;
    private final String goodbyeChannelId;
    private final Map<String, String> ticketCategories;

    public Bot(Properties p) {
        guildId = p.getProperty("guildId");
        verifyRoleId = p.getProperty("verifyRoleId");
        welcomeChannelId = p.getProperty("welcomeChannelId");
        goodbyeChannelId = p.getProperty("goodbyeChannelId");
        ticketCategories = Map.of(
                "zakup", p.getProperty("ticketCategory.zakup"),
                "pomoc", p.getProperty("ticketCategory.pomoc"),
                "inne", p.getProperty("ticketCategory.inne")
        );
    }

    public static void main(String[] args) throws IOException {
        Properties cfg = new Properties();
        try (InputStream in = Bot.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (in == null) throw new IllegalStateException("Brak config.properties w resources!");
            cfg.load(in);
        }

        JDABuilder.createDefault(cfg.getProperty("token"))
                .enableIntents(EnumSet.of(
                        GatewayIntent.GUILD_MEMBERS,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT))
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .addEventListeners(new Bot(cfg))
                .setStatus(OnlineStatus.ONLINE)
                .build();
    }

    @Override
    public void onReady(ReadyEvent e) {
        Guild guild = e.getJDA().getGuildById(guildId);
        if (guild == null) return;

        guild.updateCommands().addCommands(
                Commands.slash("setup-verify", "Wyślij panel weryfikacji"),
                Commands.slash("setup-regulamin", "Wyślij panel regulaminu"),
                Commands.slash("setup-ticket", "Wyślij panel ticketów")
        ).queue();

        System.out.println("✅ Bot online jako " + e.getJDA().getSelfUser().getName());
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent e) {
        if (!e.getGuild().getId().equals(guildId)) return;

        switch (e.getName()) {
            case "setup-verify" -> sendVerifyPanel(e);
            case "setup-regulamin" -> sendRulesPanel(e);
            case "setup-ticket" -> sendTicketPanel(e);
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent e) {
        if (!e.getComponentId().equals("verify_btn")) return;

        Role role = e.getGuild().getRoleById(verifyRoleId);
        if (role == null) {
            e.reply("❌ Rola weryfikacyjna nie istnieje.").setEphemeral(true).queue();
            return;
        }
        e.getGuild().addRoleToMember(e.getUser(), role).queue(
                s -> e.reply("✅ Zweryfikowano!").setEphemeral(true).queue(),
                f -> e.reply("❌ Nie mogę nadać roli.").setEphemeral(true).queue()
        );
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent e) {
        switch (e.getComponentId()) {
            case "rules_select" -> handleRulesSelect(e);
            case "ticket_select" -> handleTicketSelect(e);
        }
    }

    private void handleRulesSelect(StringSelectInteractionEvent e) {
        String choice = e.getValues().get(0);
        EmbedBuilder embed = new EmbedBuilder().setColor(Color.RED);

        if (choice.equals("ogolny")) {
            embed.setTitle("📌 Regulamin Ogólny")
                    .setDescription("""
                            §1. Zakazuje się wyzywania innych użytkowników  
                            §2. Zakazuje się wszelkich gróźb  
                            §3. Zakazuje się wysyłania prywatnych zdjęć/danych bez zgody  
                            §4. Zakaz  
                            §5. Zakaz promocji  
                            §6. Zakazuje się wyzywania administracji  
                            §7. Zakaz wysyłania zdjęć NSFW  
                            §8. Zakazuje się niepotrzebnego tworzenia ticketów  
                            §9. Zakazuje się ustawiania obraźliwych/niestosownych nicków  
                            §10. Zakazuje się nadmiernego pingowania  
                            §12. Szanuj innych członków  
                            §13. Zakaz floodowania  
                            §14. Zakaz spamu  
                            §15. Zakaz nadmiernego pisania capslockiem
                            """);
        } else if (choice.equals("zakup")) {
            embed.setTitle("💰 Regulamin Zakupu")
                    .setDescription("""
                            §1. Wszystkie płatności są bezzwrotne  
                            §2. Zakupione usługi wysylane sa do 15 minut po opłaceniu  
                            §3. Zakazane jest próbowanie wyłudzeń zwrotów (chargeback)  
                            §4. Reklamacje przyjmujemy wyłącznie przez ticket  
                            §5. Administracja zastrzega sobie prawo do zmiany zawartości usług  
                            §6. W przypadku naruszenia regulaminu, zakup może zostać unieważniony bez zwrotu  
                            §7. Nie udostępniaj danych płatności osobom trzecim  
                            §8. Rabaty i promocje są czasowe i mogą zostać wycofane bez ostrzeżenia
                            """);
        }

        e.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleTicketSelect(StringSelectInteractionEvent e) {
        String type = e.getValues().get(0);
        String categoryId = ticketCategories.get(type);

        if (categoryId == null) {
            e.reply("❌ Brak skonfigurowanej kategorii dla tego ticketu.").setEphemeral(true).queue();
            return;
        }

        Guild guild = e.getGuild();

        guild.createTextChannel("ticket-" + type + "-" + e.getUser().getName())
                .setParent(guild.getCategoryById(categoryId))
                .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                .addPermissionOverride(e.getMember(), EnumSet.of(Permission.VIEW_CHANNEL), null)
                .queue(chan -> {
                    EmbedBuilder first = new EmbedBuilder()
                            .setColor(Color.RED)
                            .setDescription("Opisz szczegóły swojego zgłoszenia.\nPoproś moderatora o zamknięcie, gdy sprawa zostanie rozwiązana.");

                    chan.sendMessage("🎫 **Ticket " + e.getUser().getAsMention() + "** _(typ: " + type + ")_")
                            .setEmbeds(first.build())
                            .queue();

                    e.reply("✅ Utworzono ticket: " + chan.getAsMention()).setEphemeral(true).queue();
                });
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent e) {
        if (!e.getGuild().getId().equals(guildId)) return;
        TextChannel ch = e.getGuild().getTextChannelById(welcomeChannelId);
        if (ch != null) {
            ch.sendMessageEmbeds(new EmbedBuilder()
                    .setColor(Color.GREEN)
                    .setTitle("👋 Witaj!")
                    .setDescription("Hej " + e.getUser().getAsMention()
                            + ", jesteś **" + e.getGuild().getMemberCount()
                            + "** osobą na **" + e.getGuild().getName() + "**!")
                    .build()).queue();
        }
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent e) {
        if (!e.getGuild().getId().equals(guildId)) return;
        TextChannel ch = e.getGuild().getTextChannelById(goodbyeChannelId);
        if (ch != null) {
            ch.sendMessageEmbeds(new EmbedBuilder()
                    .setColor(Color.RED)
                    .setTitle("👋 Do zobaczenia!")
                    .setDescription(e.getUser().getName() + " opuścił(a) serwer.")
                    .build()).queue();
        }
    }

    private void sendVerifyPanel(SlashCommandInteractionEvent e) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.GREEN)
                .setTitle("✅ Weryfikacja")
                .setDescription("Kliknij przycisk, aby sie zweryfikowac.");

        e.replyEmbeds(embed.build())
                .addActionRow(Button.success("verify_btn", "Zweryfikuj się"))
                .queue();
    }

    private void sendRulesPanel(SlashCommandInteractionEvent e) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.RED)
                .setTitle("📖 Regulamin BLADECORE")
                .setDescription("Nieznajomość regulaminu nie zwalnia z jego przestrzegania!\n\n"
                        + "Wybierz sekcję z listy poniżej.");

        StringSelectMenu menu = StringSelectMenu.create("rules_select")
                .setPlaceholder("Wybierz regulamin")
                .addOption("Regulamin Ogólny", "ogolny", "Zasady ogólne", Emoji.fromUnicode("📝"))
                .addOption("Regulamin Zakupu", "zakup", "Zasady zakupów", Emoji.fromUnicode("💰"))
                .build();

        e.replyEmbeds(embed.build()).addActionRow(menu).queue();
    }

    private void sendTicketPanel(SlashCommandInteractionEvent e) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.RED)
                .setTitle("🎫 Centrum Pomocy")
                .setDescription("Wybierz typ zgłoszenia, a bot utworzy prywatny kanał.");

        StringSelectMenu menu = StringSelectMenu.create("ticket_select")
                .setPlaceholder("Wybierz typ ticketu")
                .addOption("Zakup", "zakup", "Chcę coś kupić", Emoji.fromUnicode("💰"))
                .addOption("Pomoc", "pomoc", "Potrzebuję pomocy", Emoji.fromUnicode("❓"))
                .addOption("Inne", "inne", "Inne zgłoszenie", Emoji.fromUnicode("📋"))
                .build();

        e.replyEmbeds(embed.build()).addActionRow(menu).queue();
    }
}
