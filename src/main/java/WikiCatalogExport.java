import junit.framework.Assert;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static java.lang.String.format;
import static java.lang.String.valueOf;
import static java.util.stream.Collectors.summingInt;
import static java.util.stream.Collectors.toList;
import static org.apache.logging.log4j.LogManager.getLogger;

/**
 * Wiki catalog utility class that fetches wiki catalog from the Wiki website and exports it into the file.
 *
 * Examples of catalogs to fetch:
 *   * https://en.wikipedia.org/wiki/List_of_French-language_authors
 *   * https://en.wikipedia.org/wiki/List_of_Russian-language_writers
 *   * https://en.wikipedia.org/wiki/List_of_German-language_authors
 *   * https://en.wikipedia.org/wiki/List_of_English_writers
 */
public class WikiCatalogExport {

    public static void main(String[] args) throws IOException {
        WikiCatalog wikiCatalog = new WikiCatalog();
        Predicate<Element> filterOutPredicate = el -> {
            String link = el.attr("href").toLowerCase();
            return !link.contains("list_of") && !link.contains("russian_") && !link.contains("literature");
        };
        wikiCatalog.updateCatalog("en.wikipedia.org", "List_of_Russian-language_writers", "div#mw-content-text > ul > li > a:first-child", filterOutPredicate);
        wikiCatalog.updateCatalog("en.wikipedia.org", "List_of_English_writers", "div#mw-content-text > div > ul > li > a:first-child", filterOutPredicate);
        wikiCatalog.updateCatalog("en.wikipedia.org", "List_of_German-language_authors", "div#mw-content-text > dl > dd > a", filterOutPredicate);
        wikiCatalog.updateCatalog("en.wikipedia.org", "List_of_French-language_authors", "div#mw-content-text > ul > li > a:first-child", filterOutPredicate);

        wikiCatalog.exportToFile(System.getProperty("PATH_TO_EXPORT", "wiki.pages.csv"));
    }

    @Data
    public static class WikiCatalog {
        private static final Logger log = LogManager.getLogger(WikiCatalog.class);

        List<WikiPage> pages = new ArrayList<>();

        public void updateCatalog(String project, String category, String cssQuery, Predicate<Element> filterOutPredicate)  {
            List<WikiPage> wikiPages = retrieveCatalog(project, category, cssQuery, filterOutPredicate);
            pages.addAll(wikiPages);
        }

        public void exportToFile(String filePath) {
            List<String> wikiPages = pages.stream().map(WikiPage::toString).collect(toList());
            log.info("Export {} wiki pages.", wikiPages.size());
            try {
                Files.write(Paths.get(filePath), wikiPages);
            } catch (IOException e) {
                throw new RuntimeException("Could not export wiki-pages to file: " + filePath);
            }
        }

        public static List<WikiPage> retrieveCatalog(String project, String category, String cssQuery, Predicate<Element> filterOutPredicate)  {
            List<WikiPage> pages = new ArrayList<>();

            // select elements for wiki project under the given category
            String wikiCategoryPageUrl = "https://" + project + "/wiki/" + category;
            Document doc;
            try {
                doc = Jsoup.connect(wikiCategoryPageUrl).get();
            } catch (IOException e) {
                throw new RuntimeException("Could not retrieve catalog from: " + wikiCategoryPageUrl, e);
            }
            Elements elements = doc.select(cssQuery);

            // filter out elements
            List<Element> filteredElements = elements.stream().filter(filterOutPredicate).collect(toList());
            log.info("Retrieved[{}] - filtered-out[{}] = {} wiki-links from wiki-category url: {}", elements.size(), (elements.size()-filteredElements.size()), filteredElements.size(), wikiCategoryPageUrl);
            if (log.isDebugEnabled()) {
                filteredElements.stream().map(el -> "https://" + project + el.attr("href")).
                        forEach(url -> { log.debug("Page: {}", url); });
            }

            // iterate over the list
            filteredElements.stream().forEach(element -> {
                String title = canoniseTitle(element.attr("title"), project); //or use @title
                if (title == null)
                    return; //ignore wiki pages w/o title
                String id = title.replaceAll(" ", "_");
                String url = "https://" + project + "/wiki/" + id;
                WikiPage wp = WikiPage.builder().
                        project(project).
                        category(category).
                        url(url).
                        id(id).
                        title(title).
                        views(WikiPageStats.retrieveWikiPageStats(id)).
                        build();

                pages.add(wp);
            });

            return pages;
        }

        private static String canoniseTitle(String title, String project) {
            try {
                String encodedURL = new URI("https", project, "/w/api.php", "action=query&titles=" + title + "&redirects&format=json", null).toString();
                JSONObject jsonObject = (JSONObject) HttpUtils.getJSONResource(encodedURL);
                JSONObject queryResult = (JSONObject) jsonObject.get("query");
                if (((JSONObject)queryResult.get("pages")).get("-1") != null) {
                    throw new IllegalArgumentException(format("Title[%s] is not found for project: %s", title, project));
                }
                Object redirectObject = queryResult.get("redirects");
                if (redirectObject instanceof JSONArray) {
                    for (Object o : ((JSONArray) redirectObject)) {
                        if (o instanceof JSONObject) {
                            Object toTitle = ((JSONObject) o).get("to");
                            if (!(toTitle instanceof String)) {
                                throw new IllegalArgumentException("'pages/redirect[0]/to is not recognised as string: " + queryResult);
                            }
                            return (String)toTitle;
                        }
                    }
                }
            } catch (Exception e) {
                log.error(e);
                return null;
            }
            return title;
        }
    }

    /**
     * Representation of wiki page with the number of views per page.
     * A page rank is according to the number of views.
     */
    @Data
    @Builder
    public static class WikiPage implements Comparable<WikiPage> {
        private String project;
        private String category;
        private String url;
        private String id;
        private String title;
        private Integer views;

        public static String header() {
            return "url" + '|'  + "id" + '|' + "title" + '|' + "views";
        }

        @Override
        public String toString() {
            Assert.assertFalse( "Project is incorrect: " + category, category == null || category.contains("|"));
            Assert.assertFalse("Category is incorrect: " + project, project == null || project.contains("|"));
            Assert.assertFalse(     "Url is incorrect: " + url, url == null || url.contains("|"));
            Assert.assertFalse(      "Id is incorrect: " + id, id == null || id.contains("|"));
            Assert.assertFalse(   "Title is incorrect: " + title, title == null || title.contains("|"));
            Assert.assertFalse("Views is incorrect: " + views, views == null);
            return  project + '|'  +
                    category + '|' +
                    url + '|' +
                    id + '|' +
                    title + '|' +
                    views;
        }

        public static WikiPage fromString(String line) {
            String[] tokens = StringUtils.split(line, '|');
            Assert.assertEquals("Line has " + tokens.length + " while 6 are expected: " + line, 6, tokens.length);
            try {
                Integer views = Integer.parseInt(tokens[5]);
                return WikiPage.builder().project(tokens[0]).category(tokens[1]).url(tokens[2]).id(tokens[3]).title(tokens[4]).views(views).build();
            } catch (NumberFormatException e) {
                throw new RuntimeException("Number of views '" + tokens[5] + "' is not integer: " + line, e);
            }
        }

        @Override
        public int compareTo(WikiPage another) {
            return views.compareTo(another.views);
        }
    }


    /**
     * Popular pages: https://tools.wmflabs.org/pageviews/?project=en.wikipedia.org&platform=all-access&agent=user&range=latest-20&pages=Benjamin_Zephaniah|Lord_Byron|E._F._Benson
     */
    public static class WikiPageStats {
        private static final Logger log = getLogger(WikiPageStats.class);
        public static final int MAX_ERROR_RESOLUTION_ATTEMPTS = 3;
        private static String fromDate = "20150904";
        private static String toDate = "20160903";

        public static Integer retrieveWikiPageStats(String pageId) {
            try {
                Object resultObject = requestPageStats(pageId);
                log.debug(resultObject);
                Integer views = calculateTotalNumberOfViews(resultObject);
                if (views == 0) {
                    int attempts = 0;
                    while (resultObject instanceof JSONObject && ((JSONObject) resultObject).containsKey("type") && attempts< MAX_ERROR_RESOLUTION_ATTEMPTS) {
                        Thread.sleep(1000); // Give server a break
                        log.debug("Re-executing request for [{}] because of an error: {}", pageId, resultObject);
                        resultObject = requestPageStats(pageId);
                        views = calculateTotalNumberOfViews(resultObject);
                        attempts++;
                    }
                    if (views == 0) {
                        log.warn("Zero views according to the response: {}", resultObject);
                    }
                }
                log.info("Number of views for page [{}] through [{}, {}]: {}", pageId, fromDate, toDate, views);
                return views;
            } catch (Exception e) {
                throw new RuntimeException("Could not retrieve stat for page #" + pageId, e);
            }
        }

        private static Integer calculateTotalNumberOfViews(Object resultObject) {
            return filterTraverse(resultObject, jsonObject -> jsonObject.containsKey("views")).
                                stream().map(jsonObject -> Integer.parseInt(valueOf(jsonObject.get("views")))).collect(summingInt(Integer::intValue));
        }

        private static Object requestPageStats(String pageId) throws IOException, ParseException {
            String url = format("https://wikimedia.org/api/rest_v1/metrics/pageviews/per-article/en.wikipedia/all-access/user/%s/daily/%s00/%s00", pageId, fromDate, toDate);
            return HttpUtils.getJSONResource(url);
        }

        private static ArrayList<JSONObject> filterTraverse(Object jsonObject, Predicate<JSONObject> filter) {
            ArrayList<JSONObject> returnedObjects = new ArrayList<>();
            filterInternalTraverse(jsonObject, filter, returnedObjects);
            return returnedObjects;
        }

        private static void filterInternalTraverse(Object jsonObject, Predicate<JSONObject> filter, List<JSONObject> returnedObjects) {
            if (jsonObject instanceof JSONObject) {
                JSONObject obj = (JSONObject) jsonObject;
                if (filter.test(obj)) {
                    returnedObjects.add(obj);
                }
                for (Object key : obj.keySet()) {
                    filterInternalTraverse(obj.get(key), filter, returnedObjects);
                }
            } else if (jsonObject instanceof JSONArray) {
                JSONArray array = (JSONArray) jsonObject;
                for (Object object : array) {
                    filterInternalTraverse(object, filter, returnedObjects);
                }
            }
        }
    }
}