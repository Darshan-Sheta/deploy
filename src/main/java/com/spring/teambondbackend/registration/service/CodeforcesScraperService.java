package com.spring.teambondbackend.registration.service;

import com.spring.teambondbackend.registration.dto.CodeforcesStatsDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class CodeforcesScraperService {

    public CodeforcesStatsDto scrapeUserStats(String username) throws Exception {
        String url = "https://codeforces.com/profile/" + username;
        CodeforcesStatsDto stats = new CodeforcesStatsDto();

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .get();

            // Codeforces profile structure usually puts stats in the "info" block
            // Rating and Max Rating are often inside spans with class "user-gray", "user-green", etc. depending on rank
            // OR inside the <div class="info"> -> <ul>

            // Inspecting typical structure:
            // <ul>
            //   <li> ... Rating: <span ...>1234</span> (max. <span ...>1400</span>) ... </li>
            //   <li> ... Rank: <span ...>Pupil</span> ... </li>
            // </ul>

            // 1. Current Rating and Max Rating
            for (Element li : doc.select(".info ul li")) {
                String text = li.text();
                if (text.contains("Contest rating:")) {
                    // Example text: "Contest rating: 1450 (max. specialist, 1450)"
                    // Elements are <span>1450</span> and <span class="smaller"> (max. <span class="...">specialist</span>, <span class="...">1450</span>)</span>
                    
                    // Improved strategy: Select all spans within this li
                    Elements spans = li.select("span");
                    if (spans.size() >= 1) {
                         stats.setCurrentRating(spans.first().text()); // First span is current rating
                    }
                    if (spans.size() >= 3) {
                         // The last span usually contains the max rating number
                         // Structure: [Rating] (max. [Rank], [MaxRating])
                         stats.setMaxRating(spans.last().text());
                    }
                }
                if (text.contains("Rank:")) {
                   stats.setRank(li.select("span").first().text());
                }
            }
             // Fallback if not found (e.g. unrated)
            if (stats.getCurrentRating() == null) stats.setCurrentRating("0");
            if (stats.getMaxRating() == null) stats.setMaxRating("0");
            if (stats.getRank() == null) stats.setRank("Unrated");


        } catch (IOException e) {
             throw new Exception("Failed to fetch Codeforces profile: " + e.getMessage());
        }

        return stats;
    }
}
