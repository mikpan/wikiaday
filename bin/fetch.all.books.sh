curl -o books.csv -G 'https://query.wikidata.org/sparql' \
     --header "Accept: text/csv"  \
     --data-urlencode query='
 #Books with a title from EN, FR, RU, GE wiki pages 
 SELECT ?book ?title WHERE {
  ?book wdt:P31 wd:Q571;
  OPTIONAL { ?book wdt:P1476 ?title. }
  SERVICE wikibase:label { bd:serviceParam wikibase:language "en,fr,ru,ge". }
 }
 #LIMIT 100
'