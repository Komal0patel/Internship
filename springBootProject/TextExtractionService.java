package org.example.service;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import net.sourceforge.tess4j.Tesseract;
import com.opencsv.CSVReader;
import net.sourceforge.tess4j.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TextExtractionService {

    public JsonArray extractText(MultipartFile file) throws Exception {
        String extractedText = extractTextFromFile(file);
        JsonArray testResultsArray=prepareCamundaInput(extractedText);
        return testResultsArray;

    }
    public static Map<String, String> extractTestValues(String text) {
        Map<String, String> extractedTests = new LinkedHashMap<>();
        Map<String, List<String>> requiredTests = new HashMap<>();

        requiredTests.put("creatinine", Arrays.asList("creatinine", "serum creatinine"));
        requiredTests.put("bun", Arrays.asList("bun", "blood urea nitrogen"));
        requiredTests.put("sodium", Arrays.asList("sodium", "serum sodium"));
        requiredTests.put("potassium", Arrays.asList("potassium", "serum potassium"));
        requiredTests.put("uacr", Arrays.asList("uacr", "urine albumin-to-creatinine ratio", "urine acr", "albumin creatinine ratio", "urine albumin/creatinine"));

        // Improved regex: Captures variations, ignores case sensitivity
        Pattern pattern = Pattern.compile("(?i)([A-Za-z /-]+?)[:=]?\\s*([0-9]+\\.?[0-9]*)\\s*(mg/dL|mmol/L|g/L|mg/g|%)?");
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            String testName = matcher.group(1).trim().toLowerCase();
            String testValue = matcher.group(2).trim();
            String unit = matcher.group(3) != null ? matcher.group(3) : ""; // Handle missing units

            // Ignore BUN/Creatinine Ratio to avoid incorrect values
            if (testName.contains("ratio") && !testName.toLowerCase().contains("albumin")) {
                continue;
            }

            for (Map.Entry<String, List<String>> entry : requiredTests.entrySet()) {
                for (String alias : entry.getValue()) {
                    // Improved matching: Handles multi-word phrases better
                    if (testName.replaceAll("[^a-zA-Z]", " ").contains(alias.toLowerCase())) {
                        extractedTests.put(entry.getKey(), testValue + " " + unit);
                        break;
                    }
                }
            }
        }

        return extractedTests;
    }
    public static String extractTestCases(String text) {
        String regex = "(?i)\\b(creatinine|serum creatinine|sodium|potassium|chloride|electrolytes|blood urea nitrogen|bun|glomerular filtration rate|gfr|urine albumin-to-creatinine ratio|uacr|albumin creatinine ratio)\\b\\s*[:=-]?\\s*(\\d{1,3}(?:\\.\\d{1,2})?)";
        Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(text);

        StringBuilder resultBuilder = new StringBuilder();

        while (matcher.find()) {
            String testName = matcher.group(1).trim();
            String result = matcher.group(2).trim();
            resultBuilder.append(testName).append(": ").append(result).append("\n");
        }

        return resultBuilder.toString().trim();
    }
    private String extractTextFromFile(MultipartFile file) throws Exception {
        System.out.println("inside extract text function which deals with different types of file");
        String fileName = file.getOriginalFilename().toLowerCase();
        StringBuilder extractedText = new StringBuilder();
        String text="";
        String extracted="";
        if (fileName.endsWith(".pdf")) {

            try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.pdmodel.PDDocument.load(file.getInputStream())) {
                org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                text= stripper.getText(document).toLowerCase();
                System.out.println(text);
                extracted = extractTestValues(text).toString();
                System.out.println("extracted results:"+extracted);
            }
        } else if (fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".webp")) {
            try {

                System.out.println("Inside image text extraction block of code");
                File imageFile = File.createTempFile("uploaded_", file.getOriginalFilename());
                Files.copy(file.getInputStream(), imageFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                Tesseract tesseract = new Tesseract();
                //tesseract.setDatapath("D:/Downloads/Tesseract-OCR/Tesseract-OCR/tessdata"); // Set correct Tesseract path
                text = tesseract.doOCR(imageFile).toLowerCase();
                extracted = extractTestCases(text).toString();
            } catch (TesseractException e) {
                e.printStackTrace();
                return "Error extracting text from image.";
            }
        }else if(fileName.endsWith(".csv")){
            try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
                String[] line;
                while ((line = reader.readNext()) != null) {
                    extracted += String.join(" ", line) + "\n";
                }
            }
        }
        else if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) {
            try (InputStream fis = file.getInputStream();
                 Workbook workbook = WorkbookFactory.create(fis)) {
                for (Sheet sheet : workbook) {
                    for (Row row : sheet) {
                        for (Cell cell : row) {
                            extracted += cell.toString() + " ";
                        }
                        extracted += "\n";
                    }
                }
            }
        }

        else {
            throw new Exception("Unsupported file format.");
        }

        return extracted.toString();
    }

    private String extractTextFromExcel(MultipartFile file) throws Exception {
        StringBuilder extractedText = new StringBuilder();
        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            for (Sheet sheet : workbook) {
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        extractedText.append(cell.toString()).append(" ");
                    }
                }
            }
        }
        return extractedText.toString();
    }
    public static JsonArray prepareCamundaInput(String text) {
        text=text.toLowerCase();
        System.out.println("extrated text"+text);
        JsonArray testResultsArray = new JsonArray();
        String regex = "(?i)\\b(creatinine|creatinine serum|creatinine blood|creatinine level|serum creatinine|bun|blood urea nitrogen|sodium|sodium serum|serum sodium|na|potassium|serum potassium|k|gfr|glomerular filtration rate|gfr creatinine|egfr|uacr|albumin creatinine ratio)\\b\\s*[:=\\-]?\\s*(\\d{1,3}(?:\\.\\d{1,2})?)";
        Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);

        JsonObject groupedElectrolytes = new JsonObject(); // To store sodium and potassium together

        while (matcher.find()) {
            String testName = matcher.group(1).toLowerCase();
            double testValue = Double.parseDouble(matcher.group(2));
            System.out.println("Extracted Test Name: " + testName);

            // Standardizing test names
            if (testName.matches("bun|blood urea nitrogen|bun/creatinine")) {
                testName = "bun";
            } else if (testName.matches("creatinine|creatinine serum|creatinine blood|creatinine level|serum creatinine")) {
                testName = "creatinine";
            } else if (testName.matches("gfr|glomerular filtration rate|gfr creatinine|egfr")) {
                testName = "gfr";
            } else if (testName.matches("uacr|albumin creatinine ratio")) {
                testName = "uacr";
            } else if (testName.matches("sodium|sodium serum|serum sodium|na")) {
                testName = "sodium";
            } else if (testName.matches("potassium|serum potassium|k")) {
                testName = "potassium";
            }
            System.out.println("final test name Test Name: " + testName);


            JsonObject valueObject = new JsonObject();
            valueObject.addProperty("value", testValue);
            valueObject.addProperty("type", "Double");

            if (testName.equals("sodium") || testName.equals("potassium")) {
                groupedElectrolytes.add(testName, valueObject);
            } else {
                JsonObject testObject = new JsonObject();
                JsonObject variableObject = new JsonObject();
                variableObject.add(testName, valueObject);
                testObject.add("variables", variableObject);
                testResultsArray.add(testObject);
            }
        }

        // Add grouped sodium & potassium if at least one is present
        if (groupedElectrolytes.size() > 0) {
            JsonObject testObject = new JsonObject();
            testObject.add("variables", groupedElectrolytes);
            testResultsArray.add(testObject);
        }

        System.out.println("Final Extracted Test Results: " + testResultsArray);
        return testResultsArray;
    }


}
