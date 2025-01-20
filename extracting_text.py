#executed in google colab
!pip install pytesseract
!pip install pytesseract pandas
!pip install PyPDF2
!pip install pytesseract pdf2image
!pip install PyMuPDF
!pip install pdfplumber

import cv2
import pandas as pd
import pytesseract
import re
import os
from PIL import Image
import pdfplumber

# Specify the path to Tesseract if not in PATH
pytesseract.pytesseract.tesseract_cmd = r'C:\Program Files\Tesseract-OCR\tesseract.exe'

# Function to extract text from an image
def extract_text_from_image(image_path):
    image = cv2.imread(image_path)
    text = pytesseract.image_to_string(image).lower()
    return text

# Function to extract text from a PDF
def extract_text_from_pdf(file_path):
    extracted_text = []
    # Use pdfplumber to handle text-based PDFs
    with pdfplumber.open(file_path) as pdf:
        for page in pdf.pages:
            # Extract text if it's a text-based page
            text = page.extract_text()
            text=text.lower()
            if text:
                extracted_text.append(text)
            else:
               print("no text found")

    return "\n".join(extracted_text)

# Function to extract text from CSV
def extract_text_from_csv(csv_path):
    df = pd.read_csv(csv_path)
    text = df.to_string().lower()  # Convert all data into a string and make it lowercase
    return text

# Function to extract text from Excel
def extract_text_from_excel(excel_path):
    df = pd.read_excel(excel_path)
    text = df.to_string().lower()  # Convert all data into a string and make it lowercase
    return text


def clean_extracted_text(text):
    # Remove unwanted newlines and excess whitespace
    text = re.sub(r'\s+', ' ', text)  # Replace multiple spaces/newlines with a single space
    text = re.sub(r'\n+', ' ', text)  # Remove line breaks (newlines)
    text = text.strip()  # Remove leading/trailing whitespace
    return text

# Function to extract test results using regex
def extract_test_results(text):
    #pattern = r"(creatinine|urea|chloride|sodium|blood urea nitrogen \(bun\)|glomerular filtration rate \(gfr\)|Urine Albumin):?\s*(\d+\.?\d*)"
    #pattern = r"([a-z\s\(\),-]+(?:creatinine|sodium|potassium|cloride|electrolytes|blood urea nitrogen|bun|glomerular filtration rate|gfr)[a-z\s\(\),-]*)\s+(\d+\.?\d*)?"
    pattern = r"([a-z\s\(\),-]*(?:creatinine|sodium|potassium|chloride|electrolytes|blood urea nitrogen|bun|glomerular filtration rate|gfr)[\s\(\),-]*)\s+(\d+\.?\d*)"

    matches = re.findall(pattern, text, re.IGNORECASE)
    data = [{"Test Name": match[0].strip(), "Result": match[1]} for match in
            matches]
    return pd.DataFrame(data)


# Main function
def main():
    # Ask user for the file path
    file_path = input("Enter the file path (image, PDF, CSV, or Excel): ")

    if not os.path.exists(file_path):
        print(f"File not found: {file_path}")
        return

    # Check the file extension
    _, file_extension = os.path.splitext(file_path)

    if file_extension.lower() in ['.jpg', '.jpeg', '.png', '.webp']:
        # Extract text from image
        print("Processing image...")
        text = extract_text_from_image(file_path)
    elif file_extension.lower() == '.pdf':
        # Extract text from PDF
        print("Processing PDF...")
        text = extract_text_from_pdf(file_path)
    elif file_extension.lower() == '.csv':
        # Extract text from CSV
        print("Processing CSV...")
        text = extract_text_from_csv(file_path)
    elif file_extension.lower() in ['.xls', '.xlsx']:
        # Extract text from Excel
        print("Processing Excel...")
        text = extract_text_from_excel(file_path)
    else:
        print("Unsupported file type.")
        return

    # Display the extracted text
    print("Extracted Text: ")
    print(text)

    # Extract test results from the text
    text=clean_extracted_text(text)
    df = extract_test_results(text)

    # Display the extracted data
    if not df.empty:
        print("Extracted Test Results:")
        print(df)
        # Save to CSV file
        df.to_csv("extracted_test_results.csv", index=False)
        print("Extracted test results saved to 'extracted_test_results.csv'.")
    else:
        print("No test results found.")

# Run the main function
if __name__ == "__main__":
    main()
