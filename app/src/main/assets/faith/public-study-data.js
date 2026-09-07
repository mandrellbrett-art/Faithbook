(()=>{
'use strict';
window.ARKFORGE_PUBLIC_STUDY={
  churchRoles:[
    {name:'The Faithful / Congregation',kind:'Lay faithful',service:'The baptized people of God: worship, discipleship, family life, witness, charity and participation in the Church.',limits:'Human dignity is equal across every office. Church office is responsibility, not superior personhood.',refs:'Acts 2:42-47; 1 Peter 2:9'},
    {name:'Family / Domestic Church',kind:'Lay vocation',service:'Households where faith is prayed, learned, practiced and handed on in ordinary life.',limits:'A family is not a substitute for parish sacramental life or professional help when needed.',refs:'Joshua 24:15; Ephesians 6:1-4'},
    {name:'Catechist',kind:'Lay ministry',service:'Teaches and accompanies people learning the faith, Scripture and Christian life.',limits:'Teaches within the Church’s entrusted formation and does not independently redefine doctrine.',refs:'Deuteronomy 6:6-9; Matthew 28:20'},
    {name:'Lector / Reader',kind:'Lay ministry',service:'Proclaims Scripture in the liturgy and helps the assembly hear the Word clearly.',limits:'The Gospel at Mass is ordinarily proclaimed by a deacon or priest.',refs:'1 Timothy 4:13; Nehemiah 8:8'},
    {name:'Acolyte / Altar Service',kind:'Lay ministry',service:'Assists the liturgy and care of the altar according to the norms of the Church.',limits:'Does not exercise functions reserved to ordained ministers.',refs:'Psalm 84:10; Luke 22:26'},
    {name:'Cantor / Choir / Musician',kind:'Lay ministry',service:'Supports sung prayer, psalmody, hymns and congregational participation.',limits:'Music serves worship rather than becoming the center of worship.',refs:'Colossians 3:16; Psalm 150'},
    {name:'Sacristan',kind:'Lay service',service:'Prepares and cares for liturgical books, vessels, vestments and worship space.',limits:'Care and preparation do not confer ordained authority.',refs:'1 Corinthians 14:40'},
    {name:'Usher / Hospitality',kind:'Lay service',service:'Welcomes people, helps with practical needs and supports an orderly, hospitable gathering.',limits:'Hospitality must not become favoritism or exclusion.',refs:'Romans 12:13; James 2:1-4'},
    {name:'Extraordinary Minister of Holy Communion',kind:'Lay ministry',service:'Assists with distribution of Holy Communion when authorized and needed.',limits:'Extraordinary ministry does not replace the ordinary ministers or confer priestly authority.',refs:'1 Corinthians 11:23-29'},
    {name:'Religious Sister / Nun',kind:'Consecrated life',service:'Lives a consecrated vocation shaped by prayer, vows, community and a particular charism.',limits:'Religious life is a vocation, not a rank above lay people and not female priesthood.',refs:'Luke 10:38-42; Matthew 19:21'},
    {name:'Religious Brother / Monk / Friar',kind:'Consecrated life',service:'Lives consecrated life through prayer, community, work and the charism of an institute or monastery.',limits:'A religious brother is not automatically ordained; some male religious are priests and some are not.',refs:'Acts 2:42-47; Psalm 133'},
    {name:'Deacon',kind:'Holy Orders — deacon',service:'Serves in word, liturgy and charity; may baptize, proclaim the Gospel, preach, assist at Mass and perform other ministries entrusted by the Church.',limits:'Deacons do not consecrate the Eucharist or absolve sins in sacramental confession.',refs:'Acts 6:1-7; 1 Timothy 3:8-13'},
    {name:'Priest / Presbyter',kind:'Holy Orders — priest',service:'Celebrates the Eucharist, hears confessions, anoints the sick, preaches and shepherds according to the bishop’s mission.',limits:'A priest serves in communion with the bishop and within the authority entrusted to him.',refs:'John 20:21-23; 1 Peter 5:1-4'},
    {name:'Pastor',kind:'Priestly office',service:'A priest entrusted with the pastoral care and leadership of a parish.',limits:'Pastor is an office of a priest, not a separate degree of Holy Orders.',refs:'John 21:15-17; 1 Peter 5:2'},
    {name:'Bishop',kind:'Holy Orders — bishop',service:'Receives the fullness of Holy Orders and shepherds a local church/diocese in teaching, sanctifying and governance.',limits:'Episcopal authority is pastoral responsibility exercised within the communion and law of the Church.',refs:'1 Timothy 3:1-7; Titus 1:5-9'},
    {name:'Archbishop / Metropolitan',kind:'Episcopal office',service:'A bishop entrusted with an archdiocese; a metropolitan also has defined responsibilities within an ecclesiastical province.',limits:'Archbishop is not a fourth degree of Holy Orders.',refs:'Acts 15; 1 Corinthians 14:40'},
    {name:'Cardinal',kind:'Church office',service:'A member of the College of Cardinals who advises and serves the Church; eligible cardinals elect a pope in conclave.',limits:'Cardinal is not a degree of Holy Orders and does not make a person more valuable than another Christian.',refs:'Mark 10:42-45'},
    {name:'Pope / Bishop of Rome',kind:'Petrine office',service:'The Bishop of Rome and pastor of the universal Catholic Church, serving the unity and mission of the Church.',limits:'The papal office is a ministry with defined theological and canonical meaning; it is not unlimited personal authority.',refs:'Matthew 16:18-19; John 21:15-17'}
  ],
  churchStructures:[
    {name:'Parish',desc:'Local community of the faithful entrusted to a pastor under the diocesan bishop.'},
    {name:'Deanery / Vicariate',desc:'Regional grouping of parishes used in many dioceses for coordination and pastoral organization.'},
    {name:'Diocese',desc:'A local church entrusted to a bishop.'},
    {name:'Ecclesiastical Province',desc:'A metropolitan see together with related suffragan dioceses.'},
    {name:'Episcopal Conference',desc:'Bishops of a country or region cooperating in defined pastoral matters.'},
    {name:'Holy See',desc:'The ecclesiastical authority associated with the Pope and central governance of the Catholic Church.'},
    {name:'Universal Church',desc:'The worldwide communion of local churches united in Catholic faith and sacramental life.'},
    {name:'Eastern Catholic Structure',desc:'Eastern Catholic Churches may use eparchy, archeparchy, patriarch, major archbishop and other corresponding structures.'}
  ],
  vocationPaths:[
    'Marriage and family life','Single lay discipleship','Lay ministry and apostolate','Consecrated religious life','Religious brother or sister','Permanent diaconate','Seminary → transitional diaconate → priesthood'
  ],
  traditions:[
    {name:'The Eucharist',type:'Sacrament / worship',meaning:'Christ-centered sacramental worship using bread and wine according to the Last Supper tradition.',refs:'Luke 22:14-20; 1 Corinthians 11:23-29',label:'SCRIPTURE + CHURCH TEACHING'},
    {name:'Cross',type:'Symbol',meaning:'Central Christian sign recalling the crucifixion of Jesus and the saving significance Christians confess in the Cross.',refs:'1 Corinthians 1:18; Galatians 6:14',label:'SCRIPTURE + SYMBOLISM'},
    {name:'Crucifix',type:'Symbol',meaning:'A cross bearing an image of the crucified Jesus, emphasizing the Passion and sacrificial love of Christ.',refs:'John 19; 1 Corinthians 2:2',label:'TRADITION + SYMBOLISM'},
    {name:'Chalice / Cup',type:'Liturgical object',meaning:'Cup used in Eucharistic worship; linked to the cup of the Last Supper.',refs:'Matthew 26:27-29; 1 Corinthians 10:16',label:'SCRIPTURE + LITURGY'},
    {name:'Bread',type:'Biblical / liturgical symbol',meaning:'Food, provision and a central Eucharistic sign; Scripture connects bread with manna, hospitality and Jesus as the bread of life.',refs:'Exodus 16; John 6:35',label:'SCRIPTURE + LITURGY'},
    {name:'Wine',type:'Biblical / liturgical symbol',meaning:'Biblical sign of celebration, covenant imagery and the Eucharistic cup.',refs:'John 2:1-11; Matthew 26:29',label:'SCRIPTURE + LITURGY'},
    {name:'Altar',type:'Liturgical place',meaning:'Sacred table/altar around which Eucharistic worship is celebrated.',refs:'Hebrews 13:10; Revelation 6:9',label:'SCRIPTURE + LITURGY'},
    {name:'Candles / Light',type:'Symbol',meaning:'Light symbolizes Christ, witness, prayer and watchfulness.',refs:'John 8:12; Matthew 5:14-16',label:'SCRIPTURE + SYMBOLISM'},
    {name:'Incense',type:'Liturgical symbol',meaning:'Fragrant smoke traditionally associated with prayer, reverence and worship.',refs:'Psalm 141:2; Revelation 8:3-4',label:'SCRIPTURE + TRADITION'},
    {name:'Holy Water',type:'Devotional / sacramental sign',meaning:'Water used as a reminder of baptism, cleansing and blessing.',refs:'Ezekiel 36:25; John 3:5',label:'SCRIPTURE + TRADITION'},
    {name:'Oil / Anointing',type:'Biblical / sacramental sign',meaning:'Oil is associated with healing, consecration, kingship and sacramental anointing.',refs:'James 5:14; 1 Samuel 16:13',label:'SCRIPTURE + LITURGY'},
    {name:'Dove',type:'Biblical symbol',meaning:'Common Christian symbol of the Holy Spirit, especially from the baptism of Jesus.',refs:'Matthew 3:16',label:'SCRIPTURE + SYMBOLISM'},
    {name:'Fish',type:'Christian symbol',meaning:'Ancient Christian symbol associated with Jesus Christ and discipleship.',refs:'Matthew 4:19; John 21',label:'SCRIPTURE + HISTORY'},
    {name:'Lamb',type:'Biblical symbol',meaning:'Sacrificial and messianic imagery culminating in Jesus as Lamb of God in Christian interpretation.',refs:'Exodus 12; John 1:29; Revelation 5',label:'SCRIPTURE + THEOLOGY'},
    {name:'Keys',type:'Biblical / Church symbol',meaning:'Symbol of stewardship and authority, prominently connected in Catholic interpretation with Peter.',refs:'Isaiah 22:22; Matthew 16:19',label:'SCRIPTURE + TRADITION'},
    {name:'Vestments',type:'Liturgical clothing',meaning:'Special clothing used in worship to identify liturgical role and season.',refs:'Exodus 28; Revelation 7:9',label:'SCRIPTURE + TRADITION'},
    {name:'Rosary',type:'Devotional tradition',meaning:'A structured prayer devotion centered on meditation on events in the life of Jesus, often with Mary as the contemplative companion.',refs:'Luke 1-2; John 2; John 19',label:'TRADITION + SCRIPTURE MEDITATION'}
  ],
  ceremonies:[
    {name:'Mass',purpose:'Public Eucharistic worship centered on Scripture and the Eucharist.',parts:'Introductory Rites → Liturgy of the Word → Liturgy of the Eucharist → Communion → Concluding Rites'},
    {name:'Baptism',purpose:'Christian initiation through water and the Trinitarian formula.',parts:'Welcome → Word → prayer/exorcism → water → anointing/white garment/light → blessing'},
    {name:'Confirmation',purpose:'Sacramental strengthening associated with the Holy Spirit and Christian initiation.',parts:'Word → renewal of baptismal promises → laying on of hands → anointing with chrism'},
    {name:'Reconciliation',purpose:'Sacramental confession, absolution and reconciliation.',parts:'Welcome → confession → counsel/penance → act of contrition → absolution → thanksgiving'},
    {name:'Anointing of the Sick',purpose:'Prayer and sacramental anointing for seriously ill or frail persons.',parts:'Word → prayer → laying on of hands → anointing → intercession'},
    {name:'Matrimony',purpose:'Covenantal marriage celebrated before God and the Church.',parts:'Word → questions/consent → vows → rings → blessing → Eucharist when celebrated within Mass'},
    {name:'Holy Orders',purpose:'Ordination to the diaconate, priesthood or episcopate.',parts:'Calling → promises → litany → laying on of hands → prayer of ordination → investiture/anointing according to order'},
    {name:'Eucharistic Adoration',purpose:'Prayer before the Blessed Sacrament.',parts:'Exposition → prayer/silence/song → benediction where celebrated → reposition'},
    {name:'Funeral Liturgy',purpose:'Prayer for the deceased and consolation of the living in Christian hope.',parts:'Vigil may precede → Funeral Mass/liturgy → committal'},
    {name:'Easter Vigil',purpose:'Principal Easter celebration of light, Scripture, initiation and Eucharist.',parts:'Service of Light → Liturgy of the Word → Baptismal Liturgy → Eucharist'},
    {name:'Stations of the Cross',purpose:'Devotional meditation on Jesus’ Passion.',parts:'Fourteen traditional stations with Scripture, reflection and prayer'},
    {name:'Procession',purpose:'Public movement in prayer associated with feasts, Eucharistic devotion or other liturgical/devotional occasions.',parts:'Gathering → ordered procession → prayer/song → destination rite'}
  ],
  songCategories:[
    {name:'Psalms',note:'Biblical songs and prayers. Full text can be included when the Bible text source permits.'},
    {name:'Gregorian Chant',note:'Historic chant tradition. Use public-domain or appropriately licensed editions.'},
    {name:'Public-domain Hymns',note:'Full lyrics may be bundled when public-domain status is verified for the jurisdiction and edition used.'},
    {name:'Modern Worship / Hymns',note:'Store titles, Scripture links and study notes unless lyrics/audio are licensed for redistribution.'},
    {name:'Seasonal Music',note:'Advent, Christmas, Lent, Easter and other liturgical-season study collections.'},
    {name:'Eucharistic Music',note:'Songs and chants connected with Eucharistic worship and theology.'}
  ],
  logicPath:['Text','Immediate Context','Language','Historical Setting','Cross-References','Church Teaching / Tradition','Alternative Interpretations','Evidence & Uncertainty','Synthesis','Prayer & Application'],
  parableFields:['Speaker','Audience','Triggering Question / Situation','Characters','Literal Story','Historical / Social Setting','Main Teaching','Symbolic Readings','Old Testament Parallels','Traditional Christian Interpretation','Alternative Scholarly Readings','What It Does NOT Necessarily Mean','Application','Questions Remaining'],
  numberStudy:{examples:['1','3','7','12','40','70/72','144','666','1000'],rule:'Track occurrences, immediate context, literary function, historical setting and traditional interpretation. A repeated number is not automatically a prediction, secret code or personal message.'},
  sourceLabels:['SCRIPTURE','CHURCH TEACHING','TRADITION','LITURGY','CHURCH FATHER','HISTORY','ARCHAEOLOGY','LINGUISTICS','SCHOLARSHIP','ARTHUR SYNTHESIS','USER NOTE','QUESTION / UNRESOLVED']
};
})();
