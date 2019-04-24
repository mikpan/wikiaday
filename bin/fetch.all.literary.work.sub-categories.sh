curl -o sub-categories.csv -G 'https://query.wikidata.org/sparql' \
     --header "Accept: text/csv"  \
     --data-urlencode query='
#literary work sub-categories
SELECT ?sc ?label ?instance_of WHERE {
  ?sc wdt:P279 wd:Q7725634.
#  ?sc wdt:P279 wd:Q571	
#  ?sc wdt:P279 wd:Q56552233 
  OPTIONAL {
    ?sc rdfs:label ?label.
    FILTER((LANG(?label)) = "en")
  }
  OPTIONAL { ?sc wdt:P31 ?instance_of }
}
'
