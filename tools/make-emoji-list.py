"""Builds the emoji list the icon picker offers (app/src/main/assets/emoji.tsv).

Run: python3 tools/make-emoji-list.py path/to/unicode-emoji-json/data-by-group.json app/src/main/assets/emoji.tsv

The data is Unicode's emoji list, from the MIT-licensed npm package unicode-emoji-json
(https://github.com/muan/unicode-emoji-json). Each line of the output is:

    group <TAB> emoji <TAB> name <TAB> skin tone template

The template is the emoji with "~" where a skin tone modifier goes, or empty when it takes none.
"""
import json
import sys

TONE = "~"
VS16 = 0xFE0F
HANDSHAKE = 0x1F91D


def main(source, target):
    groups = json.load(open(source, encoding="utf-8"))
    # Characters that take a skin tone on their own: the single-character emoji that support it.
    bases = set()
    for group in groups:
        for e in group["emojis"]:
            cps = [ord(c) for c in e["emoji"] if ord(c) != VS16]
            if e["skin_tone_support"] and len(cps) == 1:
                bases.add(cps[0])
    lines = []
    for group in groups:
        for e in group["emojis"]:
            template = ""
            if e["skin_tone_support"]:
                cps = [ord(c) for c in e["emoji"]]
                out = []
                toned = 0
                i = 0
                while i < len(cps):
                    out.append(chr(cps[i]))
                    # In a sequence of several people, the handshake between them takes no tone.
                    if cps[i] in bases and not (len(cps) > 1 and cps[i] == HANDSHAKE):
                        out.append(TONE)
                        toned += 1
                        # The tone replaces the presentation selector after the base.
                        if i + 1 < len(cps) and cps[i + 1] == VS16:
                            i += 1
                    i += 1
                if toned:
                    template = "".join(out)
            name = e["name"].replace("\t", " ")
            lines.append("\t".join([group["name"], e["emoji"], name, template]))
    with open(target, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(len(lines), "emoji written to", target)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
