from pathlib import Path
from bs4 import BeautifulSoup

def inject_css_into_html_folder(folder, css_file):
    css_content = Path(css_file).read_text(encoding="utf-8")

    for html_path in Path(folder).rglob("*.html"):
        soup = BeautifulSoup(html_path.read_text(encoding="utf-8"), "html.parser")

        head = soup.head
        if head is None:
            # Create <head> if missing
            head = soup.new_tag("head")
            soup.html.insert(0, head)

        style_tag = head.find("style")
        if style_tag is None:
            style_tag = soup.new_tag("style")
            head.append(style_tag)

        # Avoid duplicate injection
        if css_content not in style_tag.text:
            style_tag.string = (style_tag.string or "") + "\n" + css_content

        html_path.write_text(str(soup), encoding="utf-8")

# Usage
inject_css_into_html_folder(
    folder="./build",
    css_file="./media/extra.css"
)
