// Name to give to each channel:
channels_name = [
    "C1",
    "C2",
    "C3"
];

// According the names that you just gave, what LUT should be used?
LUTs = [
    "C1": [255, 0  , 0  ],
    "C2": [0  , 0  , 255],
    "C3": [0  , 255, 0  ]
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