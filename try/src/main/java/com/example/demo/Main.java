package com.example.demo;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Pdf;
import org.openqa.selenium.PrintsPage;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.print.PrintOptions;
import org.openqa.selenium.By;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Base64;

public class Main {
    public static void main(String[] args) {
        // Setup WebDriver
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new"); // Run in background
        options.addArguments("--disable-gpu");

        WebDriver driver = new ChromeDriver(options);
        // Spring Boot Handbook URL
        String mainUrl = "https://www.codingshuttle.com/spring-boot-handbook/spring-boot-tutorial-a-comprehensive-guide-for-beginners/";
        List<String> tempPdfFiles = new ArrayList<>();

        // Output Directory Setup
        String currentProjectFolder = System.getProperty("user.dir");
        Path outputDir = Paths.get(currentProjectFolder, "Generated_PDFs");

        try {
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }
            System.out.println("📂 Saving files to: " + outputDir.toAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        try {
            System.out.println("🚀 Starting the Scraper...");

            driver.get(mainUrl);
            Thread.sleep(3000);

            List<WebElement> links = driver.findElements(By.tagName("a"));
            Set<String> uniqueUrls = new LinkedHashSet<>();

            System.out.println("🔍 Scanning for chapters...");
            String normalizedMainUrl = mainUrl.endsWith("/") ? mainUrl.substring(0, mainUrl.length() - 1) : mainUrl;

            for (WebElement link : links) {
                String href = link.getAttribute("href");
                if (href == null) continue;
                String normalizedHref = href.endsWith("/") ? href.substring(0, href.length() - 1) : href;

                if (normalizedHref.contains("spring-boot-handbook") && !normalizedHref.equals(normalizedMainUrl)) {
                    uniqueUrls.add(href);
                }
            }
            System.out.println("📋 Found " + uniqueUrls.size() + " chapters.");

            int count = 1;
            for (String url : uniqueUrls) {
                try {
                    System.out.println("Processing [" + count + "/" + uniqueUrls.size() + "]: " + url);
                    driver.get(url);

                    // --- 1. SCROLL TO LOAD IMAGES ---
                    ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight)");
                    Thread.sleep(5000); // Wait for images
                    ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0)");
                    Thread.sleep(2000);

                    // --- 2. INJECT "PURE BLACK & WHITE" CSS FIXES ---
                    // --- 👇 NEW: ROBUST DARK MODE FIX 👇 ---
                    ((JavascriptExecutor) driver).executeScript(
                            // 1. Toggle Native Dark Mode (Cleaner look)
                            "document.documentElement.classList.add('dark');" +
                                    "document.documentElement.classList.remove('light');" +

                                    // 2. Inject Custom CSS to Override Print Settings
                                    // This forces the PDF engine to respect the dark colors
                                    "var style = document.createElement('style');" +
                                    "style.innerHTML = `" +
                                    "   @media print {" +
                                    "       body, html {" +
                                    "           background-color: #000000 !important;" +
                                    "           -webkit-print-color-adjust: exact !important;" +
                                    "           print-color-adjust: exact !important;" +
                                    "       }" +
                                    "       /* Force all text to be white */" +
                                    "       h1, h2, h3, p, span, div, li, td, th {" +
                                    "           color: #ffffff !important;" +
                                    "       }" +
                                    "       /* Style Code Blocks (Dark Gray) */" +
                                    "       pre, code, .hljs {" +
                                    "           background-color: #1e1e1e !important;" +
                                    "           color: #ffffff !important;" +
                                    "           border: 1px solid #333 !important;" +
                                    "       }" +
                                    "       /* Hide Sidebar & Nav */" +
                                    "       nav, aside, footer, .sidebar, .menu, .toc, .table-of-contents {" +
                                    "           display: none !important;" +
                                    "       }" +
                                    "   }" +
                                    "`;" +
                                    "document.head.appendChild(style);" +

                                    // 3. Fallback: Manually set styles on screen just in case
                                    "document.body.style.backgroundColor = '#000000';" +
                                    "document.body.style.color = '#ffffff';"
                    );
                    Thread.sleep(2000); // Wait for styles to apply
                    // --- 👆 END OF FIX 👆 ---
                    PrintOptions printOptions = new PrintOptions();
                    printOptions.setBackground(true);
                    printOptions.setShrinkToFit(true);

                    Pdf pdf = ((PrintsPage) driver).print(printOptions);

                    String slug = url;
                    if (slug.endsWith("/")) slug = slug.substring(0, slug.length() - 1);
                    int lastSlashIndex = slug.lastIndexOf("/");
                    if (lastSlashIndex != -1) slug = slug.substring(lastSlashIndex + 1);

                    String topicName = slug.replace("-", "_");
                    String paddedCount = String.format("%02d", count);
                    String filename = outputDir.resolve(paddedCount+ "_"+ topicName + "_chapter_" + count + ".pdf").toString();

                    byte[] originalPdfBytes = Base64.getDecoder().decode(pdf.getContent());
                    try (PDDocument document = PDDocument.load(originalPdfBytes)) {
                        int totalPages = document.getNumberOfPages();
                        if (totalPages > 2) {
                            document.removePage(totalPages - 1);
                            document.removePage(totalPages - 2);
                            System.out.println("   ✂️ Removed last 2 pages.");
                        }
                        document.save(filename);
                    }

                    tempPdfFiles.add(filename);
                    count++;
                } catch (Exception e) {
                    System.err.println("⚠️ Error on page: " + url);
                }
            }

            // 3. MERGE
            if (!tempPdfFiles.isEmpty()) {
                System.out.println("📚 Merging " + tempPdfFiles.size() + " chapters...");
                PDFMergerUtility merger = new PDFMergerUtility();
                String destPath = outputDir.resolve("Full_Spring_Boot_Handbook.pdf").toString();
                merger.setDestinationFileName(destPath);

                for (String filePath : tempPdfFiles) {
                    merger.addSource(new File(filePath));
                }

                merger.mergeDocuments(null);
                System.out.println("✅ SUCCESS! Book saved at: " + destPath);
            } else {
                System.out.println("❌ No chapters found.");
            }

            // Cleanup
            for (String filePath : tempPdfFiles) {
                try { new File(filePath).delete(); } catch (Exception ignored) {}
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}