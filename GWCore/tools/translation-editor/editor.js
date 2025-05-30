const csvFile   = document.getElementById('csvFile');
const tableDiv  = document.getElementById('tableContainer');
const downloadBtn = document.getElementById('downloadBtn');
const targetLocale = document.getElementById('targetLocale');
const translateBtn = document.getElementById('translateBtn');
const deeplKey = document.getElementById('deeplKey');

let data = [];     // 2‑D array with CSV grid

csvFile.addEventListener('change', e => {
  const file = e.target.files[0];
  Papa.parse(file, {
    complete: results => {
      data = results.data;
      renderTable();
      populateLocales();
    },
    skipEmptyLines: true
  });
});

function renderTable() {
  const header = data[0];
  const table = document.createElement('table');
  table.className = 'table table-bordered table-sm';
  table.innerHTML = `<thead><tr>${header.map(h=>`<th contenteditable="false">${h}</th>`).join('')}</tr></thead>`;
  const tbody = document.createElement('tbody');
  data.slice(1).forEach((row,rIdx) => {
    const tr = document.createElement('tr');
    row.forEach((cell,cIdx) => {
      const td = document.createElement('td');
      td.contentEditable = cIdx > 0; // don't edit keys
      td.textContent = cell;
      td.addEventListener('input', () => data[rIdx+1][cIdx] = td.textContent);
      tr.appendChild(td);
    });
    tbody.appendChild(tr);
  });
  table.appendChild(tbody);
  tableDiv.innerHTML = '';
  tableDiv.appendChild(table);
}

function populateLocales(){
  targetLocale.innerHTML = '<option selected disabled value="">Translate column…</option>';
  data[0].slice(1).forEach(loc=>{
    const opt=document.createElement('option');
    opt.value=loc; opt.textContent=loc;
    targetLocale.appendChild(opt);
  });
}

targetLocale.addEventListener('change', ()=> translateBtn.disabled = false);

translateBtn.addEventListener('click', async ()=>{
  const col = data[0].indexOf(targetLocale.value);
  const key = deeplKey.value.trim();
  if (col<0 || !key){ alert('Missing locale or DeepL key'); return; }
  for (let r=1;r<data.length;r++){
    const srcText = data[r][1]; // assumes column 1 is default caption
    if (!srcText || data[r][col]) continue;
    const translated = await deeplTranslate(srcText, targetLocale.value, key);
    data[r][col] = translated;
  }
  renderTable();
});

async function deeplTranslate(text,target,key){
  const params = new URLSearchParams({text, target_lang: target, auth_key: key});
  const res = await fetch('https://api-free.deepl.com/v2/translate',{method:'POST',body:params});
  const json = await res.json();
  return json.translations?.[0]?.text || '';
}

downloadBtn.addEventListener('click', ()=>{
  const csv = Papa.unparse(data);
  const blob = new Blob([csv], {type:'text/csv;charset=utf-8;'});
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'messages.csv';
  a.click();
  URL.revokeObjectURL(url);
});
