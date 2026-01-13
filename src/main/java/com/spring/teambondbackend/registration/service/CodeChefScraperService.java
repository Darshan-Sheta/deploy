package com.spring.teambondbackend.registration.service;

import com.spring.teambondbackend.registration.dto.CodeChefStatsDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class CodeChefScraperService {

    public CodeChefStatsDto scrapeUserStats(String username) throws Exception {
        String url = "https://www.codechef.com/users/" + username;
        CodeChefStatsDto stats = new CodeChefStatsDto();

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .timeout(10000) // 10 seconds timeout
                    .get();

            // 1. Stars
            Element starsElement = doc.selectFirst(".rating-star");
            stats.setStars(starsElement != null ? starsElement.text() : "0★");

            // 2. Current Rating
            Element currentRatingElement = doc.selectFirst(".rating-number");
            stats.setCurrentRating(currentRatingElement != null ? currentRatingElement.text() : "N/A");

            // 3. Highest Rating
            Element highestRatingElement = doc.selectFirst(".rating-header small");
            if (highestRatingElement != null) {
                // Text looks like "(Highest Rating 1624)"
                String text = highestRatingElement.text();
                String number = text.replaceAll("[^0-9]", "");
                stats.setHighestRating(number);
            } else {
                stats.setHighestRating("N/A");
            }

            // 4. Ranks (Global and Country)
            Elements rankElements = doc.select(".rating-ranks ul li");
            for (Element li : rankElements) {
                if (li.text().contains("Global")) {
                    stats.setGlobalRank(li.select("a").text());
                } else if (li.text().contains("Country")) {
                    stats.setCountryRank(li.select("a").text());
                }
            }

            // 5. Country Flag
            // Usually found in user details
            Element flagElement = doc.selectFirst(".user-details-container .user-details .country-flag");
            if (flagElement != null) {
                stats.setCountryFlag(flagElement.attr("src"));
            }

        } catch (IOException e) {
            throw new Exception("Failed to fetch CodeChef profile: " + e.getMessage());
        }

        return stats;
    }
}
