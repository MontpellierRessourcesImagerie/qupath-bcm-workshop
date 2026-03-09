// Name to give to each channel:
channels_name = [
    "GFP",
    "BF"
];

// According the names that you just gave, what LUT should be used?
LUTs = [
    "BF": [255, 255  , 255  ],
    "GFP": [0  , 255  , 0]
];

// #############################################################

setChannelNames(*channels_name);

colors = [];
nC = QP.getCurrentServer().nChannels();
for (i = 0 ; i < nC ; ++i) {
    c_name = QP.getCurrentServer().getChannel(i).getName();
    c_rgb = LUTs[c_name];
    int rgb = c_rgb[0];
    rgb = (rgb << 8) + c_rgb[1];
    rgb = (rgb << 8) + c_rgb[2];
    colors << rgb;
}

QP.setChannelColors(*colors);