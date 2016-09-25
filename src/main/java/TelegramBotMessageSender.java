import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.TelegramBotAdapter;
import com.pengrad.telegrambot.request.SendMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.write;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.groupingBy;
import static org.apache.commons.lang3.StringUtils.split;

/**
 * @author mipansh
 *         Date: 2016/09/11
 */
public class TelegramBotMessageSender {

    private static final Logger logger = LogManager.getLogger(TelegramBotMessageSender.class);
    public static final Path MESSAGES_FILE_PATH = Paths.get(System.getProperty("MESSAGES_FILE_PATH", "bot.msg"));
    public static final Path IMPORT_FILE_PATH = Paths.get(System.getProperty("IMPORT_FILE_PATH", "wiki.pages.csv"));


    public static final String BASEURL = "https://api.telegram.org/bot";
    public static final String TOKEN = "248586768:AAFY8ebEYjlyq0DuBzVbQnEvC5NDwoP3eK0";

    public static final Map<String, String> AVAILABLE_CATEGORIES = new LinkedHashMap<String, String>() {{
        put("en","List_of_English_writers");
        put("fr","List_of_French-language_authors");
        put("de","List_of_German-language_authors");
        put("ru","List_of_Russian-language_writers");
    }};

    public static void main(String[] args) throws IOException {
        List<WikiCatalogExport.WikiPage> wikiPages = new ArrayList<>();
        try (Stream<String> stream = Files.lines(IMPORT_FILE_PATH)) {
            stream.forEach(line -> wikiPages.add(WikiCatalogExport.WikiPage.fromString(line)));
        }
        Map<String, TreeSet<WikiCatalogExport.WikiPage>> rankedPages = wikiPages.stream().
                collect(groupingBy(WikiCatalogExport.WikiPage::getCategory, Collectors.toCollection(TreeSet::new)));

        int dayOfMonth = LocalDateTime.now().getDayOfMonth();
        String lang = new ArrayList<>(AVAILABLE_CATEGORIES.keySet()).get(dayOfMonth % 4);
        logger.info("Language of the day {} is {}", dayOfMonth, lang);

        String category = AVAILABLE_CATEGORIES.get(lang);
        WikiCatalogExport.WikiPage page = getHighestNotYetSent(rankedPages.get(category));
        String url = getInNativeLanguageIfPossible(lang, page);
        logger.info("Wiki page of the day in " + lang + " : " + url);

        sendMessage(url);
        String message = LocalDateTime.now() + "|" + page.getId() + "|" + url;
        logger.info(message);
        write(MESSAGES_FILE_PATH, singletonList(message), UTF_8, APPEND, CREATE);
    }

    private static WikiCatalogExport.WikiPage getHighestNotYetSent(TreeSet<WikiCatalogExport.WikiPage> rankedPages) throws IOException {
        final HashSet<String> messageParts = readMessageParts();

        // check if page has already been sent
        WikiCatalogExport.WikiPage highest = rankedPages.last();
        while (messageParts.contains(highest.getId())) {
            rankedPages.pollLast();
            highest = rankedPages.last();
        }

        return highest;
    }

    private static HashSet<String> readMessageParts() throws IOException {
        try {
            Files.createFile(MESSAGES_FILE_PATH);
        } catch (IOException ignore) {
        }
        List<String> messages = Files.readAllLines(MESSAGES_FILE_PATH);
        final HashSet<String> messageParts = new HashSet<>();
        messages.stream().forEach(line -> {
            messageParts.addAll(asList(split(line, '|')));
        });
        return messageParts;
    }

    private static String getInNativeLanguageIfPossible(String lang, WikiCatalogExport.WikiPage page) throws IOException {
        String url = (page.getUrl().startsWith("http")) ? page.getUrl() : ("https://" + page.getUrl());
        if ("en".equals(lang) && url.contains("en.wikipedia"))
            return url;

        Document doc = Jsoup.connect(url).get();
        try {
            Elements nativeLink = doc.select("div#p-lang > div > ul > li > a[lang=" + lang + "]");
            url = nativeLink.get(0).attr("href");
        } catch (Exception ignore) {
            logger.info("Could not find page in native language '{}': {}", url);
        }
        return url;
    }


    private static void sendMessage(String message) {
        TelegramBot bot = TelegramBotAdapter.build(TOKEN);
        bot.execute(new SendMessage(59323870, message));
        bot.execute(new SendMessage(295144283, message));
    }

    static class Person {
        private String name;
        private int age;

        Person(String name, int age) {

            this.name = name;
            this.age = age;
        }

        @Override
        public String toString() {
            return String.format("Person{name='%s', age=%d}", name, age);
        }
    }


}
