#!/bin/bash

# Ensure pdfs folder exists and is empty
if [ -d "pdfs" ]; then
    rm -rf pdfs/*
else
    mkdir pdfs
fi

# Convert .odp files in slides starting with a digit to PDF
find slides -maxdepth 1 -type f -name "[0-9]*.odp" | while read odp_file; do
    pdf_name="$(basename "${odp_file%.odp}.pdf")"
    libreoffice --headless --convert-to pdf "$odp_file" --outdir pdfs
    # Rename output if needed (LibreOffice may output with .pdf extension already)
    if [ ! -f "pdfs/$pdf_name" ]; then
        # Find the generated PDF and rename it
        generated_pdf="$(ls pdfs/*.pdf | grep "$(basename "${odp_file%.odp}")")"
        if [ -n "$generated_pdf" ]; then
            mv "$generated_pdf" "pdfs/$pdf_name"
        fi
    fi
done