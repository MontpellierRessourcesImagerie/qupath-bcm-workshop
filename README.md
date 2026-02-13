# BioCampus workshop: What you need to know about QuPath for fluorescence and IHC images analysis

## Proposed by Montpellier Ressources Imagerie, 2026

### Architecture

- Create your notebooks in the `lectures` or `exercises` folder. The content of these folders is automatically converted to HTML.
- Reference them either in the **index** or in other notebooks so they are reachable.
- The content of `media` is automatically pushed to the LFS, so you can put your illustrations there (no heavy data).
- You can locally test to build HTML pages using the building instructions (below).

### Custom CSS

#### Insert an input icon

- "Input icon" designates the icons used to indicate what the user should press/click to trigger an action (left click, ctrl+click, ...)
- To do that, an icons pack (CC0) is present in "media"
- To insert an icon use the following markdown:

`![Kenney-xxx](../media/inputs-icons/nnn.svg)`

- The "Kenney-" prefix is mandatory within the square brackets.
- The "xxx" part is up to you.
- "nnn" is to find in the icons pack folder.

#### Insert a QuPath icon

- Some QuPath icons are present in "media/qp-icons".
- To insert a QuPath icon use the following markdown:

`![QP xxx](../media/qp-icons/nnn.png)`

- The "QP " prefix is mandatory within the square brackets.
- The "xxx" part is up to you.
- "nnn" is to find in the QuPath icons folder.

#### Insert a tip/warning/note block

- Tips, warning and notes must be in their own notebook cell.
- This kind of block is generated using the cell's tags in the IPYNB.
- In VS Code, you can do right-click > "add cell tag"
- Possible values:
    - "tip"
    - "warning"
    - "note"

### Build instructions

- Have a Python environment in which `nbconvert` and `beautifulsoup4` are installed, and activate it.
- At the same level as `lectures`, `exercises`, ... create a new `build` directory (don't jump into it).
- Make sure that your terminal's curent directory is the one containing `build`, `lectures` ...
- Run the command `sh ./build.sh`

### To-do

- [ ] Write the installation tutorial for all requirements.
- [X] Write the CSS for the warning and note blocks.

