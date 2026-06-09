package com.jobtrack.service;

import com.jobtrack.dto.EmailParseResponse;
import com.jobtrack.enums.ApplicationStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Month;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RuleBasedEmailParser implements EmailParser {

    private static final List<String> COMMON_COMPANIES = Arrays.asList(
            "Google", "Meta", "Facebook", "Apple", "Netflix", "Amazon", "Microsoft", "Stripe", "Uber", "Lyft",
            "Airbnb", "Tesla", "Twitter", "GitHub", "Salesforce", "Adobe", "Figma", "Zoom", "Slack", "Spotify",
            "LinkedIn", "TikTok", "Oracle", "IBM", "Intel", "Cisco", "Nvidia", "Coinbase", "Plaid", "Pinterest",
            "Snapchat", "Twitter", "Square", "Block", "Robinhood", "Shopify", "Atlassian", "Datadog"
    );

    private static final List<String> COMMON_TITLES = Arrays.asList(
            "Software Engineer", "Frontend Developer", "Backend Developer", "Full Stack Developer",
            "Software Developer", "Data Scientist", "Product Manager", "QA Engineer", "DevOps Engineer",
            "Solutions Architect", "Systems Analyst", "Technical Program Manager", "Mobile Engineer",
            "iOS Developer", "Android Developer", "Engineering Manager", "UI/UX Designer", "Product Designer"
    );

    @Override
    public EmailParseResponse parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return EmailParseResponse.builder()
                    .confidenceScore("LOW")
                    .needsReview(true)
                    .build();
        }

        String companyName = extractCompany(rawText);
        String jobTitle = extractJobTitle(rawText);
        
        // Detect Status and Stage
        ApplicationStatus status = extractStatus(rawText);
        String stage = determineStage(status, rawText);

        // Recruiter Email
        String recruiterEmail = extractRecruiterEmail(rawText);

        // Source
        String source = extractSource(rawText);

        // Date
        LocalDate importantDate = extractImportantDate(rawText);

        // Suggested Notes
        String suggestedNotes = generateSuggestedNotes(rawText, status, companyName, jobTitle);

        // Confidence and Review Logic
        boolean hasCompany = companyName != null && !companyName.isBlank();
        boolean hasTitle = jobTitle != null && !jobTitle.isBlank();
        boolean hasConfStatus = hasConfidentStatus(rawText);

        String confidenceScore;
        if (hasCompany && hasTitle && hasConfStatus) {
            confidenceScore = "HIGH";
        } else if (hasCompany && hasTitle) {
            confidenceScore = "MEDIUM";
        } else {
            confidenceScore = "LOW";
        }

        boolean needsReview = "LOW".equals(confidenceScore) || !hasCompany || !hasTitle;

        return EmailParseResponse.builder()
                .companyName(companyName != null ? companyName.trim() : "")
                .jobTitle(jobTitle != null ? jobTitle.trim() : "")
                .status(status)
                .stage(stage)
                .recruiterEmail(recruiterEmail)
                .source(source)
                .importantDate(importantDate)
                .suggestedNotes(suggestedNotes)
                .confidenceScore(confidenceScore)
                .needsReview(needsReview)
                .build();
    }

    private String extractCompany(String text) {
        // First try to look for exact common company names in the text
        for (String company : COMMON_COMPANIES) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(company) + "\\b", Pattern.CASE_INSENSITIVE);
            if (p.matcher(text).find()) {
                return company;
            }
        }

        // Regex patterns to match "at Company", "role/career/position/opportunity at Company"
        String[] patterns = {
                "(?i)(?:welcome to|interest in|applying to|application to|careers? at|opportunity at|role at|position at|job at|team at|at)\\s+([A-Z][A-Za-z0-9\\s\\.\\-&']{1,25})",
                "(?i)(?:Subject:.*?(?:at|with|from|for))\\s+([A-Z][A-Za-z0-9\\s\\.\\-&']{1,25})",
                "(?i)(?:career with|opportunity with|position with|role with|interview with|chat with)\\s+([A-Z][A-Za-z0-9\\s\\.\\-&']{1,25})"
        };

        for (String pat : patterns) {
            Pattern p = Pattern.compile(pat);
            Matcher m = p.matcher(text);
            if (m.find()) {
                String candidate = m.group(1).trim();
                // Clean up the candidate: take only up to first punctuation or common stop-words
                return cleanCompanyName(candidate);
            }
        }

        return "";
    }

    private String cleanCompanyName(String company) {
        // Split by newlines, double spaces, or common stop-words/conjunctions
        String[] splitters = {"\\r?\\n", "\\s{2,}", "\\bfor\\b", "\\bon\\b", "\\bwith\\b", "\\bteam\\b", "\\bcareers?\\b", "\\bopportunity\\b", "\\bposition\\b", "\\brole\\b", "\\bto\\b", "\\bco\\b", "\\band\\b"};
        String cleaned = company;
        for (String splitter : splitters) {
            cleaned = cleaned.split(splitter)[0];
        }
        
        // Remove trailing punctuation
        cleaned = cleaned.replaceAll("[\\.,;:!\\?]$", "").trim();

        // Keep at most 3 words
        String[] words = cleaned.split("\\s+");
        if (words.length > 3) {
            cleaned = words[0] + " " + words[1] + " " + words[2];
        }

        return cleaned;
    }

    private String extractJobTitle(String text) {
        // Try common titles first
        for (String title : COMMON_TITLES) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(title) + "\\b", Pattern.CASE_INSENSITIVE);
            if (p.matcher(text).find()) {
                return title;
            }
        }

        // Regex pattern search for "position of X", "role of X"
        String[] patterns = {
                "(?i)(?:position of|role of|position as|role as|for the|for a)\\s+([A-Za-z0-9\\s\\-\\/]{3,35})\\s+(?:role|position|opportunity|job|application|team)",
                "(?i)(?:application for the|application for a|application for|applying for the|applying for a|applying for)\\s+([A-Za-z0-9\\s\\-\\/]{3,35})"
        };

        for (String pat : patterns) {
            Pattern p = Pattern.compile(pat);
            Matcher m = p.matcher(text);
            if (m.find()) {
                String candidate = m.group(1).trim();
                return cleanJobTitle(candidate);
            }
        }

        return "";
    }

    private String cleanJobTitle(String title) {
        String cleaned = title;
        String[] splitters = {"\\r?\\n", "\\s{2,}", "\\bat\\b", "\\bwith\\b", "\\bfor\\b", "\\bin\\b", "\\bco\\b"};
        for (String splitter : splitters) {
            cleaned = cleaned.split(splitter)[0];
        }
        return cleaned.replaceAll("[\\.,;:!\\?]$", "").trim();
    }

    private ApplicationStatus extractStatus(String text) {
        String lowerText = text.toLowerCase();

        // 1. Rejection keywords
        if (lowerText.contains("unfortunately") ||
                lowerText.contains("not moving forward") ||
                lowerText.contains("unable to offer") ||
                lowerText.contains("decided to move in another direction") ||
                lowerText.contains("decided to move forward with other") ||
                lowerText.contains("not selected") ||
                lowerText.contains("thank you for your time") ||
                lowerText.contains("wish you the best")) {
            return ApplicationStatus.REJECTED;
        }

        // 2. Offer keywords
        if (lowerText.contains("pleased to offer") ||
                lowerText.contains("congratulations") ||
                lowerText.contains("offer letter") ||
                lowerText.contains("formal offer") ||
                lowerText.contains("happy to offer") ||
                lowerText.contains("job offer")) {
            return ApplicationStatus.OFFER;
        }

        // 3. Interview keywords
        if (lowerText.contains("interview") ||
                lowerText.contains("schedule a call") ||
                lowerText.contains("phone screen") ||
                lowerText.contains("video call") ||
                lowerText.contains("zoom") ||
                lowerText.contains("google meet") ||
                lowerText.contains("availability") ||
                lowerText.contains("time slots") ||
                lowerText.contains("schedule some time") ||
                lowerText.contains("meet with") ||
                lowerText.contains("speak with")) {
            return ApplicationStatus.INTERVIEW;
        }

        // 4. Assessment keywords
        if (lowerText.contains("assessment") ||
                lowerText.contains("take-home") ||
                lowerText.contains("hackerrank") ||
                lowerText.contains("codility") ||
                lowerText.contains("coding challenge") ||
                lowerText.contains("technical test") ||
                lowerText.contains("assignment") ||
                lowerText.contains("online assessment")) {
            return ApplicationStatus.ASSESSMENT;
        }

        // 5. Applied confirmation keywords
        if (lowerText.contains("thank you for applying") ||
                lowerText.contains("received your application") ||
                lowerText.contains("submission") ||
                lowerText.contains("applied to") ||
                lowerText.contains("confirming receipt") ||
                lowerText.contains("successfully submitted") ||
                lowerText.contains("thanks for submitting")) {
            return ApplicationStatus.APPLIED;
        }

        return ApplicationStatus.APPLIED;
    }

    private String determineStage(ApplicationStatus status, String text) {
        switch (status) {
            case REJECTED:
                return "Rejected";
            case OFFER:
                return "Offer";
            case INTERVIEW:
                if (text.toLowerCase().contains("phone screen") || text.toLowerCase().contains("phone interview")) {
                    return "Phone Screen";
                }
                if (text.toLowerCase().contains("technical interview")) {
                    return "Technical Interview";
                }
                if (text.toLowerCase().contains("onsite") || text.toLowerCase().contains("on-site")) {
                    return "On-site Interview";
                }
                return "Interview";
            case ASSESSMENT:
                return "Assessment";
            case APPLIED:
            default:
                if (text.toLowerCase().contains("in review") || text.toLowerCase().contains("reviewing your application")) {
                    return "In Review";
                }
                return "Applied";
        }
    }

    private boolean hasConfidentStatus(String text) {
        String lowerText = text.toLowerCase();
        return lowerText.contains("unfortunately") ||
                lowerText.contains("not moving forward") ||
                lowerText.contains("unable to offer") ||
                lowerText.contains("pleased to offer") ||
                lowerText.contains("congratulations") ||
                lowerText.contains("offer letter") ||
                lowerText.contains("interview") ||
                lowerText.contains("schedule a call") ||
                lowerText.contains("phone screen") ||
                lowerText.contains("assessment") ||
                lowerText.contains("hackerrank") ||
                lowerText.contains("codility") ||
                lowerText.contains("thank you for applying") ||
                lowerText.contains("received your application") ||
                lowerText.contains("confirming receipt");
    }

    private String extractRecruiterEmail(String text) {
        // 1. Look for From: header
        Pattern fromPat = Pattern.compile("(?i)From:\\s*(?:[^\\n<]*<)?([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6})");
        Matcher fromMat = fromPat.matcher(text);
        if (fromMat.find()) {
            return fromMat.group(1).trim();
        }

        // 2. Scan text for any email and filter out automated ones
        Pattern emailPat = Pattern.compile("([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6})");
        Matcher emailMat = emailPat.matcher(text);
        while (emailMat.find()) {
            String email = emailMat.group(1).trim();
            String emailLower = email.toLowerCase();
            if (!emailLower.contains("noreply") &&
                    !emailLower.contains("no-reply") &&
                    !emailLower.contains("bounce") &&
                    !emailLower.contains("donotreply") &&
                    !emailLower.contains("notifications") &&
                    !emailLower.contains("info") &&
                    !emailLower.contains("support")) {
                return email;
            }
        }

        return "";
    }

    private String extractSource(String text) {
        String[] sources = {"LinkedIn", "Indeed", "Glassdoor", "ZipRecruiter", "Otta", "Wellfound", "AngelList", "Workday", "Simplihired", "Monster"};
        for (String src : sources) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(src) + "\\b", Pattern.CASE_INSENSITIVE);
            if (p.matcher(text).find()) {
                return src;
            }
        }
        return "Email";
    }

    private LocalDate extractImportantDate(String text) {
        // Regex for date patterns
        // YYYY-MM-DD
        Pattern isoPat = Pattern.compile("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b");
        Matcher isoMat = isoPat.matcher(text);
        if (isoMat.find()) {
            try {
                return LocalDate.of(Integer.parseInt(isoMat.group(1)),
                        Integer.parseInt(isoMat.group(2)),
                        Integer.parseInt(isoMat.group(3)));
            } catch (Exception e) {}
        }

        // Month Name Day, Year (e.g. June 20, 2026 or Jun 20, 2026)
        String monthsPattern = "(jan[a-z]*|feb[a-z]*|mar[a-z]*|apr[a-z]*|may|jun[a-z]*|jul[a-z]*|aug[a-z]*|sep[a-z]*|oct[a-z]*|nov[a-z]*|dec[a-z]*)";
        Pattern namedPat = Pattern.compile("(?i)\\b" + monthsPattern + "\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,\\s*(\\d{4}))?\\b");
        Matcher namedMat = namedPat.matcher(text);
        if (namedMat.find()) {
            try {
                String monthStr = namedMat.group(1).substring(0, 3).toLowerCase();
                Month month = getMonthFromAbbreviation(monthStr);
                int day = Integer.parseInt(namedMat.group(2));
                int year = namedMat.group(3) != null ? Integer.parseInt(namedMat.group(3)) : LocalDate.now().getYear();
                return LocalDate.of(year, month, day);
            } catch (Exception e) {}
        }

        // Day Month Name, Year (e.g. 20 June 2026 or 20 Jun 2026)
        Pattern reversePat = Pattern.compile("(?i)\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+" + monthsPattern + "\\.?\\s*(\\d{4})?\\b");
        Matcher reverseMat = reversePat.matcher(text);
        if (reverseMat.find()) {
            try {
                int day = Integer.parseInt(reverseMat.group(1));
                String monthStr = reverseMat.group(2).substring(0, 3).toLowerCase();
                Month month = getMonthFromAbbreviation(monthStr);
                int year = reverseMat.group(3) != null ? Integer.parseInt(reverseMat.group(3)) : LocalDate.now().getYear();
                return LocalDate.of(year, month, day);
            } catch (Exception e) {}
        }

        // DD/MM/YYYY or MM/DD/YYYY
        Pattern slashPat = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2,4})\\b");
        Matcher slashMat = slashPat.matcher(text);
        if (slashMat.find()) {
            try {
                int first = Integer.parseInt(slashMat.group(1));
                int second = Integer.parseInt(slashMat.group(2));
                int year = Integer.parseInt(slashMat.group(3));
                if (year < 100) {
                    year += 2000;
                }

                // If first digit > 12, it's definitely DD/MM/YYYY
                if (first > 12) {
                    return LocalDate.of(year, second, first);
                } else {
                    // Default to MM/DD/YYYY
                    return LocalDate.of(year, first, second);
                }
            } catch (Exception e) {}
        }

        return null;
    }

    private Month getMonthFromAbbreviation(String abb) {
        switch (abb) {
            case "jan": return Month.JANUARY;
            case "feb": return Month.FEBRUARY;
            case "mar": return Month.MARCH;
            case "apr": return Month.APRIL;
            case "may": return Month.MAY;
            case "jun": return Month.JUNE;
            case "jul": return Month.JULY;
            case "aug": return Month.AUGUST;
            case "sep": return Month.SEPTEMBER;
            case "oct": return Month.OCTOBER;
            case "nov": return Month.NOVEMBER;
            case "dec": return Month.DECEMBER;
            default: throw new IllegalArgumentException("Unknown month: " + abb);
        }
    }

    private String generateSuggestedNotes(String rawText, ApplicationStatus status, String company, String title) {
        // Create a summary paragraph based on status
        StringBuilder sb = new StringBuilder();
        sb.append("Email imported on ").append(LocalDate.now()).append(".\n");
        sb.append("Detected Status: ").append(status).append("\n");
        
        // Find subject or header if exists
        Pattern subPat = Pattern.compile("(?i)Subject:\\s*(.*?)\\r?\\n");
        Matcher subMat = subPat.matcher(rawText);
        if (subMat.find()) {
            sb.append("Subject: ").append(subMat.group(1).trim()).append("\n");
        }

        sb.append("\nEmail Body Snippet:\n");
        
        // Clean rawText a bit for notes (take first 300 chars, clean lines)
        String cleanedBody = rawText;
        // Strip Subject: / From: / To: headers from start of body snippet if long
        cleanedBody = cleanedBody.replaceAll("(?i)(Subject|From|To|Date):.*?\\r?\\n", "");
        cleanedBody = cleanedBody.trim();
        
        if (cleanedBody.length() > 300) {
            sb.append(cleanedBody.substring(0, 297)).append("...");
        } else {
            sb.append(cleanedBody);
        }

        return sb.toString();
    }
}
