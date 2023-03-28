# Cucumber
A CLI tool to help with the installation of minecraft modpacks.
All it needs is the import zip that can be gotten from curse forge
client. Running will give all the generic setup stuff needed for a
server to be setup. You just need to run it.

## Usage
### Basic
```bash
# help
java -jar Cucumber --help
# setup into given folder
java -jar Cucumber pack.zip -o path/to/mod-pack/folder
```

### Sign
signs the `eula.txt` file
```bash
# signs eula.txt
java -jar Cucumber pack.zip -o path/to/mod-pack/folder --sign
```

### Update
```bash
# updates the pack
java -jar Cucumber pack.zip -o path/to/mod-pack/folder --update
```
## Future
Fix any bugs found from "Dogfooding" and maybe colors. Like stuff
that isnt mine is gray.