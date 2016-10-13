import com.pengrad.telegrambot.Callback;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.TelegramBotAdapter;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.GetUpdates;
import com.pengrad.telegrambot.response.GetUpdatesResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.function.Function;

/**
 * Telegram bot that subscribes users to daily wiki lists
 */
public class TelegramWikiBot {

    private static final Logger logger = LogManager.getLogger(TelegramWikiBot.class);


    public static void main(String[] args) {
        TelegramBot bot = TelegramBotAdapter.build(TelegramBotMessageSender.TOKEN);
        bot.execute(new GetUpdates(), new Callback<GetUpdates, GetUpdatesResponse>() {
            @Override
            public void onResponse(GetUpdates getUpdates, GetUpdatesResponse updatesResponse) {
                // process all the updates from users and send back the responses to each and every one
                updatesResponse.updates().stream().map(new Function<Update, Object>() {
                    @Override
                    public Object apply(Update update) {
                        update.message();
                        return null;
                    }
                });
            }

            @Override
            public void onFailure(GetUpdates getUpdates, IOException e) {
                logger.error("Cannot get updates", e);
            }
        });
    }

    public class TelegramUser {
        private String id;
        private String firstName;
        private String lastName;
    }
}
